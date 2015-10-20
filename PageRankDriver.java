/*
/ building and running:
/ $ HADOOP_CLASSPATH="${hadoop classpath}"
/ $ mkdir pagerank_classes
/ $ javac -classpath $(HADOOP_CLASSPATH) -d pagerank_classes *.java
/ $ jar -cvf /home/hadoop/PageRank.jar -C pagerank_classes/ .
/
/ $ hadoop ./PageRank.jar PageRankDriver
**/

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.*;

import java.text.NumberFormat;
import java.text.DecimalFormat;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class PageRankDriver extends Configured implements Tool {

    private static NumberFormat nf = new DecimalFormat("00");
    private Integer numNodes;
    private Integer numEdges;
    private Integer numIterations;
    private Map<String, List<String>> outlinks;
    private Map<String, Float> pageranks;

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new Configuration(), new PageRankDriver(), args));
        // TODO: we could maybe specify the exact input file as an arg...
    }

    @Override
    public int run(String[] args) throws Exception {
        boolean isCompleted;
        String lastResultPath = null;

        System.out.println("Constructing input file...");
        prepareInitialInputFile("/pagerank/graph.txt"); // TODO: call this before doing anything, to set up input file.
        System.out.println("Initial input file ready.");

        // TODO: num iterations should go in this loop
        // numRuns should become this.numIterations
        int numRuns = 2;
        for (int curRun = 1; curRun <= numRuns; curRun++) {
            System.out.println("Executing iteration " + curRun + " of " + numRuns);
            String inPath = "/pagerank/input/iter" + nf.format(curRun);
            lastResultPath = "/pagerank/input/iter" + nf.format(curRun + 1);

            isCompleted = calculate(inPath, lastResultPath);
            // TODO: after each run, rewrite the output file of the run
            // to have it in the proper format for the next iteration
            // UNLESS IT'S THE LAST RUN

            if (!isCompleted)
            {
              System.out.println("something broke.");
              return 1;
            }

            if (curRun < numRuns)
            {
              transformOutputFile(lastResultPath);
            }
        }

        return 0;
    }

    // TODO: rewrite me so I take a map of nid:adj_list pairs, a map of
    // nid:pagerank pairs, and a path to which to write the file.
    // actually, we should have two methods -- one that reads the initial input file
    // and transforms it into the right format while getting the info from it,
    // and another that just rewrites a MR output file into a MR input file.
    private void prepareInitialInputFile(String inputPath) throws Exception, IOException
    {
      Configuration config = new Configuration();
      config.addResource(new Path("/HADOOP_HOME/conf/core-site.xml"));
      config.addResource(new Path("/HADOOP_HOME/conf/hdfs-site.xml"));
      String fromNodeId;
      String toNodeId;
      this.outlinks = new HashMap<String, List<String>>();
      this.pageranks = new HashMap<String, Float>();

      BufferedReader br = null;
      try
      {
        // TODO: read /pagerank/graph.txt, construct hashmap of nid:node pairs,
        // then write it to /pagerank/input/iter01
        FileSystem fs = FileSystem.get(config);
        Path path = new Path(inputPath);
        br = new BufferedReader(new InputStreamReader(fs.open(path)));
        String line;

        line = br.readLine();
        // pull out num nodes and num edges
        this.numNodes = Integer.parseInt(line.split("\\s+")[0]);
        System.out.println("Number of nodes is: " + this.numNodes);
        this.numEdges = Integer.parseInt(line.split("\\s+")[1]);
        System.out.println("Number of edges is: " + this.numEdges);

        line = br.readLine();
        // pull out num iterations to run
        this.numIterations = Integer.parseInt(line.trim());
        System.out.println("Number of iterations is: " + numIterations);

        line = br.readLine();
        while (line != null)
        {
          // pull out the outlink and add it to the proper node
          fromNodeId = line.split("\\s+")[0].trim();
          toNodeId = line.split("\\s+")[1].trim();

          if (this.outlinks.get(fromNodeId) == null)
          { // if fromNode not in table already, add it and add the outlink
            System.out.println("Adding node " + fromNodeId + " to table.");
            this.outlinks.put(fromNodeId, new ArrayList<String>());
            System.out.println("Adding link from " + fromNodeId + " to " + toNodeId + " to the table.");
            this.outlinks.get(fromNodeId).add(toNodeId);
          } else
          { // otherwise, just add toNodeId to the outlinks of fromNodeId
            this.outlinks.get(fromNodeId).add(toNodeId);
          }

          line = br.readLine();
        }
      }
      catch (Exception e)
      {
        //
      } finally
      {
        try
        {
          if (br != null)
          {
            br.close();
          }
        } catch (IOException e)
        {
          //
        }
      }

      for (String nodeId : this.outlinks.keySet())
      {
        System.out.println("Node " + nodeId + " has outlinks to: ");
        for (String neighbor : this.outlinks.get(nodeId))
        {
          System.out.println("\t--" + neighbor);
        }
      }

      BufferedWriter bw = null;
      try
      {
        FileSystem fs = FileSystem.get(config);
        Path path = new Path("/pagerank/input/iter01");
        bw = new BufferedWriter(new OutputStreamWriter(fs.create(path, true)));
        String line;
        Float initValue = (new Float(1)) / (new Float(this.numNodes));
        // TODO: build line containing nid init_rank and outlinks
        StringBuilder sb;

        for (String nodeId : this.outlinks.keySet())
        {
          this.pageranks.put(nodeId, initValue);
          sb = new StringBuilder();
          sb.append(nodeId);
          sb.append(" ");
          sb.append(initValue);
          sb.append(" ");
          for (String outlinkId : this.outlinks.get(nodeId))
          {
            sb.append(outlinkId);
            sb.append(" ");
          }
          sb.append("\n");
          System.out.println("Writing line: " + sb.toString());
          bw.write(sb.toString());
        }

      } catch (Exception e)
      {
        //
      } finally
      {
        try
        {
          if (bw != null)
          {
            bw.close();
          }
        } catch (IOException e)
        {
          //
        }
      }
    }

    private void transformOutputFile(String outputDir)
    {
        // TODO: read in the new pagerank value for each node from the output file,
        // then store them in the table (this.pageranks).
        // then use that table and the outlinks table to rewrite a new input file for the next iteration.

        // NOTE: filename is part-r-00000, if i'm interested in hardcoding it. :)
        Configuration config = new Configuration();
        config.addResource(new Path("/HADOOP_HOME/conf/core-site.xml"));
        config.addResource(new Path("/HADOOP_HOME/conf/hdfs-site.xml"));

        BufferedReader br = null;
        String inputPath = outputDir + "/part-r-00000";

        String nid;
        Float newValue;
        try
        {
          // then write it to /pagerank/input/iter00
          FileSystem fs = FileSystem.get(config);
          Path path = new Path(inputPath);
          br = new BufferedReader(new InputStreamReader(fs.open(path)));
          String line;

          line = br.readLine();
          while (line != null)
          {
            // get the new pagerank value for each node and save to table
            nid = line.split("\\s+")[0].trim();
            newValue = Float.parseFloat(line.split("\\s+")[1]);
            System.out.println("new pagerank value for node " + nid + " is " + newValue);
            this.pageranks.put(nid, newValue);

            line = br.readLine();
          }
        }
        catch (Exception e)
        {
          //
        } finally
        {
          try
          {
            if (br != null)
            {
              br.close();
            }
          } catch (IOException e)
          {
            //
          }
        }

        // TODO: write the stuff from both tables back to the same output file.
        BufferedWriter bw = null;
        try
        {
          FileSystem fs = FileSystem.get(config);
          Path path = new Path(inputPath);
          bw = new BufferedWriter(new OutputStreamWriter(fs.create(path, true)));
          String line;
          StringBuilder sb;

          for (String nodeId : this.outlinks.keySet())
          {
            sb = new StringBuilder();
            sb.append(nodeId);
            sb.append(" ");
            sb.append(this.pageranks.get(nodeId));
            sb.append(" ");
            for (String outlinkId : this.outlinks.get(nodeId))
            {
              sb.append(outlinkId);
              sb.append(" ");
            }
            sb.append("\n");
            System.out.println("Writing line: " + sb.toString());
            bw.write(sb.toString());
          }

        } catch (Exception e)
        {
          //
        } finally
        {
          try
          {
            if (bw != null)
            {
              bw.close();
            }
          } catch (IOException e)
          {
            //
          }
        }
    }

    private boolean calculate(String inputPath, String outputPath) throws IOException, ClassNotFoundException, InterruptedException
    {
        Configuration conf = new Configuration();

        Job pageRank = Job.getInstance(conf, "PageRank");
        pageRank.setJarByClass(PageRankDriver.class);

        pageRank.setInputFormatClass(NLineInputFormat.class);
        pageRank.getConfiguration().setInt("mapreduce.input.lineinputformat.linespermap", 1);
        pageRank.setOutputKeyClass(Text.class);
        pageRank.setOutputValueClass(FloatWritable.class);
        pageRank.setOutputFormatClass(TextOutputFormat.class);

        FileInputFormat.setInputPaths(pageRank, new Path(inputPath));
        FileOutputFormat.setOutputPath(pageRank, new Path(outputPath));

        pageRank.setMapperClass(PageRankMapper.class);
        pageRank.setReducerClass(PageRankReduce.class);

        return pageRank.waitForCompletion(true);
    }

}
