import java.io.IOException;
import java.net.URI;
import java.util.Vector;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;

public class DataJoin {

	// 定义输入路径
	private static final String INPUT_PATH = "input";
	// 定义输出路径
	private static final String OUT_PATH = "output";

	public static  class JoinMapper extends Mapper<LongWritable, Text, Text, Text>{
		@Override
		protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context) throws IOException, InterruptedException {
			  //获取输入文件的全路径和名称
			  FileSplit fileSplit = (FileSplit) context.getInputSplit();
			  String path = fileSplit.getPath().toString();
			  
			  //获取输入记录的字符串
			  String line = value.toString();
			  
			  //抛弃空记录
			  if (line == null || line.equals("")){
				  return;
			  }
			  
			  //处理来自tb_a表的记录
			  if (path.contains("Persons")){
				  //按制表符切割
				  String[] values = line.split(",");
				  //当数组长度小于5时，视为无效记录
				  if (values.length < 5){
					  return;
				  }
				  //获取id和name
				  String id_P = values[0];
				  String LastName = values[1];
				  String FirstName = values[2];
				  String Address = values[3];
				  String city = values[4];
				  
				  //把结果写出去
				  context.write(new Text(id_P), new Text("Persons#" + id_P + "," + LastName + "," + FirstName + "," + Address + "," + city));
			  } else if (path.contains("Orders")){
				  //按制表符切割
				  String[] values = line.split(",");
				  //当长度不为3时，视为无效记录
				  if (values.length < 3){
					  return;
				  }
				  
				  //获取属性
				  String id_O = values[0];
				  String OrderNo = values[1];
				  String id_P = values[2];
				  
				  //写出去
				  context.write(new Text(id_P), new Text("Orders#" + id_O + "," + OrderNo));
			  }
		}
	}
	
	public static class JoinReducer extends Reducer<Text, Text, Text, Text>{
		
		@Override
		protected void reduce(Text key, Iterable<Text> values, Reducer<Text, Text, Text, Text>.Context context) throws IOException, InterruptedException {
			
			//用来存放来自Persons表的数据
			Vector<String> vectorA = new Vector<String>();
			//用来存放来自Orders表的数据
			Vector<String> vectorB = new Vector<String>();
			
			//迭代集合数据
			for (Text val : values){
				//将集合中的数据对应添加到Vector中
				if (val.toString().startsWith("Persons#")){
					String[] temp = val.toString().split("#");
					vectorA.add(temp[1]);
				} else if (val.toString().startsWith("Orders#")){
					String[] temp = val.toString().split("#");
					vectorB.add(temp[1]);
				}
			}
			
			//获取两个Vector集合的长度
			int sizeA = vectorA.size();
			int sizeB = vectorB.size();
			
			//遍历两个向量将结果写出去
			for (int i=0; i<sizeA; i++){
				for (int j=0; j<sizeB; j++){
					context.write(key, new Text(vectorA.get(i) + "," + vectorB.get(j)));
				}
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	public static void main(String[] args) {

		try {
			// 创建配置信息
			Configuration conf = new Configuration();
			

			// 创建文件系统
			FileSystem fileSystem = FileSystem.get(new URI(OUT_PATH), conf);
			// 如果输出目录存在，就删除
			if (fileSystem.exists(new Path(OUT_PATH))) {
				fileSystem.delete(new Path(OUT_PATH), true);
			}

			// 创建任务
			Job job = new Job(conf, DataJoin.class.getName());

			//1.1	设置输入目录和设置输入数据格式化的类
			FileInputFormat.setInputPaths(job, INPUT_PATH);
			job.setInputFormatClass(TextInputFormat.class);

			//1.2	设置自定义Mapper类和设置map函数输出数据的key和value的类型
			job.setMapperClass(JoinMapper.class);
			job.setMapOutputKeyClass(Text.class);
			job.setMapOutputValueClass(Text.class);

			//1.3	设置分区和reduce数量(reduce的数量，和分区的数量对应，因为分区为一个，所以reduce的数量也是一个)
			job.setPartitionerClass(HashPartitioner.class);
			job.setNumReduceTasks(1);

			//1.4	排序
			//1.5	归约
			//2.1	Shuffle把数据从Map端拷贝到Reduce端。
			//2.2	指定Reducer类和输出key和value的类型
			job.setReducerClass(JoinReducer.class);
			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);

			//2.3	指定输出的路径和设置输出的格式化类
			FileOutputFormat.setOutputPath(job, new Path(OUT_PATH));
			job.setOutputFormatClass(TextOutputFormat.class);

			// 提交作业 退出
			System.exit(job.waitForCompletion(true) ? 0 : 1);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
