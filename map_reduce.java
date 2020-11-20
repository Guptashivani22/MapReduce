//What are the top 100 reviewer first names?  What is the average rating "overall" for each of these names?
import java.io.IOException;
import java.util.regex.*;
import java.util.Set;
import java.util.Map;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.*;

import com.google.gson.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * This Map-Reduce code will go through every Amazon review in rfox12:reviews
 * It will then output data on the top-level JSON keys
 */
public class AmazonReviewAnalyzeFields extends Configured implements Tool {
	// Just used for logging
	protected static final Logger LOG = LoggerFactory.getLogger(AmazonReviewAnalyzeFields.class);

	// This is the execution entry point for Java programs
	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(HBaseConfiguration.create(), new AmazonReviewAnalyzeFields(), args);
		System.exit(res);
	}

	public int run(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("Need 1 argument (hdfs output path), got: " + args.length);
			return -1;
		}

		// Now we create and configure a map-reduce "job"     
		Job job = Job.getInstance(getConf(), "AmazonReviewAnalyzeFields");
		job.setJarByClass(AmazonReviewAnalyzeFields.class);
    
    		// By default we are going to can every row in the table
		Scan scan = new Scan();
		scan.setCaching(500);        // 1 is the default in Scan, which will be bad for MapReduce jobs
		scan.setCacheBlocks(false);  // don't set to true for MR jobs

    		// This helper will configure how table data feeds into the "map" method
		TableMapReduceUtil.initTableMapperJob(
			"rfox12:reviews_10000",        	// input HBase table name
			scan,             		// Scan instance to control CF and attribute selection
			MapReduceMapper.class,   	// Mapper class
			Text.class,             	// Mapper output key
			IntWritable.class,		// Mapper output value
			job,				// This job
			true				// Add dependency jars (keep this to true)
		);

		// Specifies the reducer class to used to execute the "reduce" method after "map"
    		job.setReducerClass(MapReduceReducer.class);

    		// For file output (text -> number)
    		FileOutputFormat.setOutputPath(job, new Path(args[0]));  // The first argument must be an output path
    		job.setOutputKeyClass(Text.class);
    		job.setOutputValueClass(IntWritable.class);
    
    		// What for the job to complete and exit with appropriate exit code
		return job.waitForCompletion(true) ? 0 : 1;
	}

	public static class MapReduceMapper extends TableMapper<Text, IntWritable> {
		private static final Logger LOG = LoggerFactory.getLogger(MapReduceMapper.class);
    
    		// Here are some static (hard coded) variables
		private static final byte[] CF_NAME = Bytes.toBytes("cf");			// the "column family" name
		private static final byte[] QUALIFIER = Bytes.toBytes("review_data");	// the column name
		private final static IntWritable one = new IntWritable(1);			// a representation of "1" which we use frequently
    
		private Counter rowsProcessed;  	// This will count number of rows processed
		private JsonParser parser;		// This gson parser will help us parse JSON

		// This setup method is called once before the task is started
		@Override
		protected void setup(Context context) {
			parser = new JsonParser();
			rowsProcessed = context.getCounter("AmazonReviewAnalyzeFields", "Rows Processed");
    		}
  
  		// This "map" method is called with every row scanned.  
		@Override
		public void map(ImmutableBytesWritable rowKey, Result value, Context context) throws InterruptedException, IOException {
			try {
				// Here we get the json data (stored as a string) from the appropriate column
				String jsonString = new String(value.getValue(CF_NAME, QUALIFIER));
				
				// Now we parse the string into a JsonElement so we can dig into it
				JsonElement jsonTree = parser.parse(jsonString);

				JsonObject jsonObject = jsonTree.getAsJsonObject();
				
                String reviewerName = jsonObject.get("reviewerName").getAsString();
           		
           		Integer overall = jsonObject.get("overall").getAsInteger();

           		Integer vote = jsonObject.get("vote").getAsInteger();

           		// Sort the vote in asc
           		votes = (-1) * vote; 

           		context.write(new LongWritable(votes),new LongWritable(overall), new Text(movie_name));
                              

				}
			
				// Here we increment a counter that we can read when the job is done
				rowsProcessed.increment(1);
			} catch (Exception e) {
				LOG.error("Error in MAP process: " + e.getMessage(), e);
			}
		}
	}
  
	// Reducer to simply sum up the values with the same key (text)
	// The reducer will run until all values that have the same key are combined
	public static class MapReduceReducer extends Reducer<LongWritable,LongWritable 
                                      Text, LongWritable, Text> {

   static int count; 
  
    @Override
    public void setup(Context context) throws IOException, 
                                     InterruptedException 
    { 
        count = 0; 
    } 

 @Override
    public void reduce(LongWritable key,LongWritable overall ,Iterable<Text> values, 
      Context context) throws IOException, InterruptedException 
    { 
   
        long votes = (-1) * key.get(); 
  		

        String review_name = null; 
  
  		int avg_rating = 0;
        for (Text val : values)  
        { 
            review_name = val.toString();
            overall_rate = overall.get();
            total_rating += overall_rate; 
        } 
  			
  		avg_rating = total_rating/100;
        // we just write top 100 records as output 
        if (count < 100) 
        { 
            context.write(new LongWritable(votes),new LongWritable(avg_rating), 
                                  new Text(review_name)); 
            count++; 
        } 
    } 
} 
