/*
/  TODO:
/   -- write driver
      -- see https://github.com/abij/hadoop-wiki-pageranking/blob/master/src/com/xebia/sandbox/hadoop/WikiPageRanking.java
      -- driver needs to parse graph.txt and construct initial input file into mapreduce
      -- each intermediate result should be written to its own file
    -- write mapper
      -- see https://github.com/abij/hadoop-wiki-pageranking/blob/master/src/com/xebia/sandbox/hadoop/job2/calculate/RankCalculateMapper.java

    -- write reducer
      -- see https://github.com/abij/hadoop-wiki-pageranking/blob/master/src/com/xebia/sandbox/hadoop/job2/calculate/RankCalculateReduce.java
**/

/*
/ mapper gets node_id, cur_pagerank_val, and outlink_list.
/ for each nid in outlink_list:
/    emit(nid, cur_pagerank_val/len(outlink_list))
/
////////////////////////////////////////////////////////
/
/ each reducer gets list of pagerank_vals for given nid
/ emit(nid, sum(pagerank_vals))
/
////////////////////////////////////////////////////////
/
/ do i really need to emit the graph structure?
/ can't the driver take care of that?
/
////////////////////////////////////////////////////////
/
/ so, my driver needs to initially build an input file
/ containing lines of data such as:
/ "nid init_pagerank outlink1 outlink2 outlink3 ... outlinkn"
////////////////////////////////////////////////////////
/
/ building and running:
/  $ mkdir Gender_classes
/ $ javac -classpath ${HADOOP_HOME}/hadoop-${HADOOP_VERSION}-core.jar -d PageRank_classes PageRank.java
/ $ jar -cvf /home/hadoop/PageRank.jar -C PageRank_classes/ .
/
/ $ hadoop PageRank.jar PageRank input_path output_path
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

// TODO: move mapper and reducer to their own classes,
// add run() method to this class, following example of
// github pagerank (so this class should extend Tool or whatever.)
// so main() should just call run().
public class PageRankDriver extends Configured implements Tool {

    /*public static void main(String[] args) throws Exception {
      JobConf conf = new JobConf(PageRank.class);
      conf.setJobName("PageRank");
      conf.setOutputKeyClass(Text.class);
      conf.setOutputValueClass(LongWritable.class);
      conf.setInputFormat(TextInputFormat.class);
      conf.setOutputFormat(TextOutputFormat.class);
      conf.setMapperClass(Map.class);

      if (args.length != 2) {
          System.out.println("Usage:");
          System.out.println("/path/to/input/files /path/to/output");
          System.exit(1);
      }

      String graphDefPath = args[0];
      // TODO: open graphDefPath
      // read num nodes
      // initialize list of nodes
      // read num edges?
      // for adjacency in the rest of file:
      //    add adjacent node id to list for node
      // finally, write each nid, init pagerank, and adj list to input file.

      String inputPath = "/pagerank/init_input.data";
      FileInputFormat.setInputPaths(conf, new Path(inputPath));
      //FileInputFormat.setInputPaths(conf, new Path(args[0]));
      FileOutputFormat.setOutputPath(conf, new Path(args[1]));
      JobClient.runJob(conf);
    }*/
    private static NumberFormat nf = new DecimalFormat("00");


    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new Configuration(), new PageRankDriver(), args));
        // TODO: we could specify the exact input file as an arg...
    }

    @Override
    public int run(String[] args) throws Exception {
        boolean isCompleted;
        String lastResultPath = null;

        System.out.println("Constructing input file...");
        prepareInputFile();
        System.out.println("Initial input file ready.");

        // TODO: num iterations should go in this loop
        int numRuns = 1;
        for (int curRun = 0; curRun < numRuns; curRun++) {
            System.out.println("Executing iteration " + curRun + " of " + numRuns);
            String inPath = "pagerank/input/iter" + nf.format(curRun);
            lastResultPath = "pagerank/input/iter" + nf.format(curRun + 1);

            isCompleted = calculate(inPath, lastResultPath);

            if (!isCompleted)
            {
              System.out.println("something broke.");
              return 1;
            }
        }

        return 0;
    }

    private void prepareInputFile() throws Exception, IOException
    {
      Configuration config = new Configuration();
      config.addResource(new Path("/HADOOP_HOME/conf/core-site.xml"));
      config.addResource(new Path("/HADOOP_HOME/conf/hdfs-site.xml"));
      Integer numNodes = 0;
      Integer numEdges = 0;
      Integer numIterations = 0;
      String fromNodeId;
      String toNodeId;
      Map<String, List<String>> nodes = new HashMap<String, List<String>>();

      BufferedReader br = null;
      try
      {
        // TODO: read /pagerank/graph.txt, construct hashmap of nid:node pairs,
        // then write it to /pagerank/input/iter00
        FileSystem fs = FileSystem.get(config);
        Path path = new Path("/pagerank/graph.txt");
        br = new BufferedReader(new InputStreamReader(fs.open(path)));

        String line;

        line = br.readLine();
        // pull out num nodes and num edges
        numNodes = Integer.parseInt(line.split("\\s+")[0]);
        System.out.println("Number of nodes is: " + numNodes);
        numEdges = Integer.parseInt(line.split("\\s+")[1]);
        System.out.println("Number of edges is: " + numEdges);

        line = br.readLine();
        // pull out num iterations to run
        numIterations = Integer.parseInt(line.trim());
        System.out.println("Number of iterations is: " + numIterations);

        line = br.readLine();
        while (line != null)
        {
          // pull out the outlink and add it to the proper node
          fromNodeId = line.split("\\s+")[0].trim();
          toNodeId = line.split("\\s+")[1].trim();

          if (nodes.get(fromNodeId) == null)
          { // if fromNode not in table already, add it and add the outlink
            System.out.println("Adding node " + fromNodeId + " to table.");
            nodes.put(fromNodeId, new ArrayList<String>());
            System.out.println("Adding link from " + fromNodeId + " to " + toNodeId + " to the table.");
            nodes.get(fromNodeId).add(toNodeId);
          } else
          { // otherwise, just add toNodeId to the outlinks of fromNodeId
            nodes.get(fromNodeId).add(toNodeId);
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

      for (String nodeId : nodes.keySet())
      {
        System.out.println("Node " + nodeId + " has outlinks to: ");
        for (String neighbor : nodes.get(nodeId))
        {
          System.out.println("\t--" + neighbor);
        }
      }

      BufferedWriter bw = null;
      try
      {
        FileSystem fs = FileSystem.get(config);
        Path path = new Path("/pagerank/input/iter00");
        bw = new BufferedWriter(new OutputStreamWriter(fs.create(path, true)));
        String line;
        Float initValue = (new Float(1)) / (new Float(numNodes));
        // TODO: build line containing nid init_rank and outlinks
        StringBuilder sb;

        for (String nodeId : nodes.keySet())
        {
          sb = new StringBuilder();
          sb.append(nodeId);
          sb.append(" ");
          sb.append(initValue);
          sb.append(" ");
          for (String outlinkId : nodes.get(nodeId))
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

      // TODO: might need to return the num nodes, num edges, and num iterations??
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
