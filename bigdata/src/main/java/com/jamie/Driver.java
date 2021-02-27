package com.jamie;

import com.jamie.cache.DistributedCacheMapper;
import com.jamie.departjson.MyOutPutFormat;
import com.jamie.departjson.OuputManyMapper;
import com.jamie.departjson.OuputManyReduce;
import com.jamie.departjson.Output1Mapper;
import com.jamie.flowsum.Counter;
import com.jamie.flowsum.FlowCountMapper;
import com.jamie.flowsum.FlowCountReducer;
import com.jamie.flowsum.MyPartitioner;
import com.jamie.friends.*;
import com.jamie.index.*;
import com.jamie.inputformat.SequenceFileMapper;
import com.jamie.inputformat.SequenceFileReducer;
import com.jamie.inputformat.WholeFileInputformat;
import com.jamie.kv.KVTextMapper;
import com.jamie.kv.KVTextReducer;
import com.jamie.log.LogMapper;
import com.jamie.nline.NLineMapper;
import com.jamie.nline.NLineReducer;
import com.jamie.order.*;
import com.jamie.outputformat.FilterMapper;
import com.jamie.outputformat.FilterOutputFormat;
import com.jamie.outputformat.FilterReducer;
import com.jamie.table.Table;
import com.jamie.table.TableMapper;
import com.jamie.table.TableReducer;
import com.jamie.topn.OrderCleanUpMapper;
import com.jamie.topn.OrderCleanUpReducer;
import com.jamie.wordcount.WordcountCombiner;
import com.jamie.wordcount.WordcountMapper;
import com.jamie.wordcount.WordcountReducer;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueLineRecordReader;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

import static com.jamie.Utils.initJob;

public class Driver {
    public static final String OUTPUT_PATH = "src/main/resources/out";

    /*
计算同一个手机号码的总流量

136	1
136	1
137	2
137	3
138	2
139	1
140	2

预期
136	2...
137	5...
138	2...
139	1...
140	2...
     */
    @Test
    public void objectAsValue() throws InterruptedException, IOException, ClassNotFoundException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, FlowCountMapper.class, FlowCountReducer.class, Text.class, Counter.class, Text.class, Counter.class, "/phone");
        job.waitForCompletion(true);
    }

    /*
手机号136、137、138、139开头都分别放到一个独立的4个文件中，其他开头的放到一个文件中

136	1
136	1
137	2
137	3
138	2
139	1
140	2
     */
    @Test
    public void partition2ManyFile() throws InterruptedException, IOException, ClassNotFoundException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, FlowCountMapper.class, FlowCountReducer.class, Text.class, Counter.class, Text.class, Counter.class, "/phone");
        // 自定义分区规则
        job.setPartitionerClass(MyPartitioner.class);
        // 指定reduce 数量
        job.setNumReduceTasks(5);
        job.waitForCompletion(true);
    }

    /*
    词频统计
    a b c
    v b
    a d a

    预期
    a	3
    b	2
    c	1
    d	1
    v	1
     */
    @Test
    public void wordCount() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, WordcountMapper.class, WordcountReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/words");
        job.waitForCompletion(true);
    }

    /*
各节点自身进行reduce，减少reduce 阶段的处理数据量，减少网络IO
map -> combine -> reduce

Combine input records=36
Combine output records=5

a b c
v b
a d a

预期
a	3
b	2
c	1
d	1
v	1
     */
    @Test
    public void childNodeReduce() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, WordcountMapper.class, WordcountReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/words");
        //设置 combine
        job.setCombinerClass(WordcountCombiner.class);
        job.waitForCompletion(true);
    }

    /**
     * map端压缩????
     */
    @Test
    public void mapCompress() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));

        Configuration configuration = new Configuration();
        // 开启map端输出压缩
        configuration.setBoolean("mapreduce.map.output.compress", true);
        // 设置map端输出压缩方式
        configuration.setClass("mapreduce.map.output.compress.codec", BZip2Codec.class, CompressionCodec.class);

        Job job = initJob(configuration, Driver.class, WordcountMapper.class, WordcountReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/words");

        job.waitForCompletion(true);
    }

    /*
reduce 端压缩

输入
a b c
v b
a d a

输出
BZh91AY&SY�N�  ɀ 08 <    1 0 z����]��B@E:�
     */
    @Test
    public void redueCompress() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));

        Job job = initJob(Driver.class, WordcountMapper.class, WordcountReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/words");

        // 设置reduce端输出压缩开启
        FileOutputFormat.setCompressOutput(job, true);
        // 设置压缩的方式
        FileOutputFormat.setOutputCompressorClass(job, BZip2Codec.class);
//	    FileOutputFormat.setOutputCompressorClass(job, GzipCodec.class);
//	    FileOutputFormat.setOutputCompressorClass(job, DefaultCodec.class);

        job.waitForCompletion(true);
    }

    /*
输出前3，map 排序，reduce 排序

map cleanup, reduce cleanup
java 1
html 2
php 1
spring 6
python 4
ruby 3

预期
spring	6...
python	4...
ruby	3...

     */
    @Test
    public void mrCleanUpSort() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, OrderCleanUpMapper.class, OrderCleanUpReducer.class, Counter.class, Text.class, Text.class, Counter.class, "/top10");
        job.waitForCompletion(true);
    }

    /*
reduce 合并两个表

1001	1
1002	2
1003	3
1004	1
1005	2
1006	3

1	小米
2	华为
3	格力

输出
1004	1	小米
1001	1	小米
1005	2	华为
1002	2	华为
1006	3	格力
1003	3	格力
     */
    @Test
    public void joinTable() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, TableMapper.class, TableReducer.class, Text.class, Table.class, Table.class, NullWritable.class, "/table");
        job.waitForCompletion(true);
    }

    /*
字段比对，清洗特殊符号，生成关联id

{"base":{"code":"xm","name":"project"},"comp":"mt","list":[{"ACode":"aaaa","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"bbb","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}
{"base":{"code":"xm2","name":"project2"},"comp":"mt","list":[{"ACode":"ccc","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"eee","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}
{"base":{"code":"xm3","name":"project3"},"comp":"mt","list":[{"ACode":"ddd","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"fff","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}

预期
base
{"code":"xm3","tree_id":1357867087288074254,"parent_id":1357867087288074248,"name":"project3"}
{"code":"xm2","tree_id":1357867087288074247,"parent_id":1357867087288074241,"name":"project2"}
{"code":"xm","tree_id":1357867087225159687,"parent_id":1357867087225159681,"name":"project"}

list
{"AName":"Product1","tree_id":1357867087288074249,"BList":[{"tree_id":1357867087288074250,"parent_id":1357867087288074249,"BName":"Feature1","BCode":"gn1"},{"tree_id":1357867087288074251,"parent_id":1357867087288074249,"BName":"Feature2","BCode":"gn2"}],"parent_id":1357867087288074248,"ACode":"ddd"}
{"AName":"Product2","tree_id":1357867087288074252,"BList":[{"tree_id":1357867087288074253,"parent_id":1357867087288074252,"BName":"Feature1","BCode":"gn1"}],"parent_id":1357867087288074248,"ACode":"fff"}
{"AName":"Product1","tree_id":1357867087288074242,"BList":[{"tree_id":1357867087288074243,"parent_id":1357867087288074242,"BName":"Feature1","BCode":"gn1"},{"tree_id":1357867087288074244,"parent_id":1357867087288074242,"BName":"Feature2","BCode":"gn2"}],"parent_id":1357867087288074241,"ACode":"ccc"}
{"AName":"Product2","tree_id":1357867087288074245,"BList":[{"tree_id":1357867087288074246,"parent_id":1357867087288074245,"BName":"Feature1","BCode":"gn1"}],"parent_id":1357867087288074241,"ACode":"eee"}
{"AName":"Product1","tree_id":1357867087225159682,"BList":[{"tree_id":1357867087225159683,"parent_id":1357867087225159682,"BName":"Feature1","BCode":"gn1"},{"tree_id":1357867087225159684,"parent_id":1357867087225159682,"BName":"Feature2","BCode":"gn2"}],"parent_id":1357867087225159681,"ACode":"aaaa"}
{"AName":"Product2","tree_id":1357867087225159685,"BList":[{"tree_id":1357867087225159686,"parent_id":1357867087225159685,"BName":"Feature1","BCode":"gn1"}],"parent_id":1357867087225159681,"ACode":"bbb"}

    */
    @Test
    public void oneJson2ManyFile() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));

        Job job = initJob(Driver.class, OuputManyMapper.class, OuputManyReduce.class, Text.class, Text.class, NullWritable.class, Text.class, "/json2");

        LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
        //自定义输出文件名
        job.setOutputFormatClass(MyOutPutFormat.class);

        job.waitForCompletion(true);
    }

    /*
字段比对，清洗特殊符号

{"base":{"code":"xm","name":"project"},"comp":"mt","list":[{"ACode":"aaaa","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"bbb","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}
{"base":{"code":"xm2","name":"project2"},"comp":"mt","list":[{"ACode":"ccc","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"eee","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}
{"base":{"code":"xm3","name":"project3"},"comp":"mt","list":[{"ACode":"ddd","AName":"Product1","BList":[{"BCode":"gn1","BName":"Feature1"},{"BCode":"gn2","BName":"Feature2"}]},{"ACode":"fff","AName":"Product2","BList":[{"BCode":"gn1","BName":"Feature1"}]}]}

输出
{"comp":"mt!!","snow_id":1359777462509154305,"parent_snow_id":0,"list":[{"snow_id":1359777462530125825,"AName":"Product1!!","BList":[{"snow_id":1359777462530125826,"BName":"Feature1!!","parent_snow_id":1359777462530125825,"BCode":"gn1!!"},{"snow_id":1359777462530125827,"BName":"Feature2!!","parent_snow_id":1359777462530125825,"BCode":"gn2!!"}],"ACode":"aaaa!!","parent_snow_id":1359777462509154305},{"snow_id":1359777462530125828,"AName":"Product2!!","BList":[{"snow_id":1359777462530125829,"BName":"Feature1!!","parent_snow_id":1359777462530125828,"BCode":"gn1!!"}],"ACode":"bbb!!","parent_snow_id":1359777462509154305}],"base":{"snow_id":1359777462530125830,"code":"xm!!","name":"project!!","parent_snow_id":1359777462509154305}}
{"comp":"mt!!","snow_id":1359777462815338498,"parent_snow_id":0,"list":[{"snow_id":1359777462815338499,"AName":"Product1!!","BList":[{"snow_id":1359777462815338500,"BName":"Feature1!!","parent_snow_id":1359777462815338499,"BCode":"gn1!!"},{"snow_id":1359777462815338501,"BName":"Feature2!!","parent_snow_id":1359777462815338499,"BCode":"gn2!!"}],"ACode":"ccc!!","parent_snow_id":1359777462815338498},{"snow_id":1359777462815338502,"AName":"Product2!!","BList":[{"snow_id":1359777462815338503,"BName":"Feature1!!","parent_snow_id":1359777462815338502,"BCode":"gn1!!"}],"ACode":"eee!!","parent_snow_id":1359777462815338498}],"base":{"snow_id":1359777462815338504,"code":"xm2!!","name":"project2!!","parent_snow_id":1359777462815338498}}
{"comp":"mt!!","snow_id":1359777462815338505,"parent_snow_id":0,"list":[{"snow_id":1359777462815338506,"AName":"Product1!!","BList":[{"snow_id":1359777462815338507,"BName":"Feature1!!","parent_snow_id":1359777462815338506,"BCode":"gn1!!"},{"snow_id":1359777462815338508,"BName":"Feature2!!","parent_snow_id":1359777462815338506,"BCode":"gn2!!"}],"ACode":"ddd!!","parent_snow_id":1359777462815338505},{"snow_id":1359777462815338509,"AName":"Product2!!","BList":[{"snow_id":1359777462815338510,"BName":"Feature1!!","parent_snow_id":1359777462815338509,"BCode":"gn1!!"}],"ACode":"fff!!","parent_snow_id":1359777462815338505}],"base":{"snow_id":1359777462815338511,"code":"xm3!!","name":"project3!!","parent_snow_id":1359777462815338505}}

     */
    @Test
    public void jsonEtl() throws IOException, ClassNotFoundException, InterruptedException {
        String inputPath = "src/main/resources/json3";
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJarByClass(Driver.class);
        job.setMapperClass(Output1Mapper.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        //不进行reduce
        job.setNumReduceTasks(0);
        //自定义输出文件名
        job.setOutputFormatClass(MyOutPutFormat.class);

        FileInputFormat.setInputPaths(job, inputPath);
        FileOutputFormat.setOutputPath(job, new Path(OUTPUT_PATH));

        job.waitForCompletion(true);
    }

    /*
分文件输出

http://www.baidu.com
http://www.google.com
http://cn.bing.com
http://www.atguigu.com
http://www.sohu.com
http://www.sohu.com
http://www.sina.com
http://www.sin2a.com
http://www.sin2desa.com
http://www.sindsafa.com

预期
atguigu.log
http://www.atguigu.com

other.log
http://cn.bing.com
http://www.baidu.com
http://www.google.com
http://www.sin2a.com
http://www.sin2desa.com
http://www.sina.com
http://www.sindsafa.com
http://www.sohu.com

     */
    @Test
    public void logFilter() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));

        Job job = initJob(Driver.class, FilterMapper.class, FilterReducer.class, Text.class, NullWritable.class, Text.class, NullWritable.class, "/outputformat");
        job.setOutputFormatClass(FilterOutputFormat.class);

        job.waitForCompletion(true);
    }


    /*
    WritableComparable 对象重写排序方法

    1 222.8
    2 722.4
    1 33.8
    3 232.8
    3 33.8
    2 522.8
    2 122.4

    输出
    1	222.8
    1	33.8
    2	722.4
    2	522.8
    2	122.4
    3	232.8
    3	33.8

     */
    @Test
    public void beanOrder() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, OrderMapper.class, OrderReducer.class, OrderBean.class, NullWritable.class, OrderBean.class, NullWritable.class, "/orderinfo");
        job.waitForCompletion(true);
    }


    /*
计算每一个订单中最贵的商品

1 222.8
2 722.4
1 33.8
3 232.8
3 33.8
2 522.8
2 122.4

输出
1	222.8
2	722.4
3	232.8

     */
    @Test
    public void order1() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, OrderMapper.class, OrderReducer.class, OrderBean.class, NullWritable.class, OrderBean.class, NullWritable.class, "/orderinfo");
        job.setGroupingComparatorClass(OrderGroupingComparator.class);
        job.waitForCompletion(true);
    }

    /*
    指定切片数量
    number of splits:3

    输入文件一共9行
    每个切片分3行
    需要3个切片

    a b c
    v b
    a d a
    s g h
    d
    d f d
    a s a
    d f a
    q w s

    输出
    a	6
    b	2
    c	1
    d	5
    f	2
    g	1
    h	1
    q	1
    s	3
    v	1
    w	1

     */
    @Test
    public void splitNumline() throws Exception {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, NLineMapper.class, NLineReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/words");
        // 设置每个切片InputSplit中划分三条记录
        NLineInputFormat.setNumLinesPerSplit(job, 3);
        // 使用NLineInputFormat处理记录数
        job.setInputFormatClass(NLineInputFormat.class);
        job.waitForCompletion(true);
    }

    /**
     * 去除日志中字段长度小于等于11的日志
     */
    @Test
    public void noReduceLog() throws IOException, ClassNotFoundException, InterruptedException {
        String inputPath = "src/main/resources/log";

        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);

        job.setJarByClass(Driver.class);
        job.setMapperClass(LogMapper.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        //不进行reduce
        job.setNumReduceTasks(0);

        FileInputFormat.setInputPaths(job, inputPath);
        FileOutputFormat.setOutputPath(job, new Path(OUTPUT_PATH));

        job.waitForCompletion(true);
    }

    /*
map 阶段分词，统计key的个数

key1 v1
key2 v2
key2 v3
key3 v4

输出
key1	1
key2	2
key3	1

     */
    @Test
    public void kv() throws Exception {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Configuration conf = new Configuration();
        //在map 阶段，根据分割符把一行数据分成key 和 value
        conf.set(KeyValueLineRecordReader.KEY_VALUE_SEPERATOR, " ");
        Job job = initJob(conf, Driver.class, KVTextMapper.class, KVTextReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/kv");
        job.setInputFormatClass(KeyValueTextInputFormat.class);
        job.waitForCompletion(true);
    }

    /**
     * 将多个小文件合并成一个SequenceFile文件
     */
    @Test
    public void sequence() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, SequenceFileMapper.class, SequenceFileReducer.class, Text.class, BytesWritable.class, Text.class, BytesWritable.class, "/format");
        // 设置输入的inputFormat
        job.setInputFormatClass(WholeFileInputformat.class);
        // 设置输出的outputFormat
        job.setOutputFormatClass(SequenceFileOutputFormat.class);
        job.waitForCompletion(true);
    }

    /**
     * 从多个文件，找出每个词所在文件，出现次数
     */
    @Test
    public void index1() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, OneIndexMapper.class, OneIndexReducer.class, Text.class, IntWritable.class, Text.class, IntWritable.class, "/index");
        job.waitForCompletion(true);
    }

    /*

A:B,C,D,F,E,O
B:A,C,E,K
C:F,A,D,I
D:A,E,F,L
E:B,C,D,M,L

输出
A	D,B,C,
B	A,E,
C	B,E,A,
D	A,C,E,
E	B,D,A,
F	A,D,C,
I	C,
K	B,
L	D,E,
M	E,
O	A,

     */
    @Test
    public void friend() throws IOException, ClassNotFoundException, InterruptedException {
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, FriendsMapper.class, FriendsReducer.class, Text.class, Text.class, Text.class, Text.class, "/friends");
        job.waitForCompletion(true);
    }

    /*
order
1001	1
1002	2
1003	3
1004	1
1005	2
1006	3

product
1	小米
2	华为
3	格力

预期
1001	1	小米
1002	2	华为
1003	3	格力
1004	1	小米
1005	2	华为
1006	3	格力
     */
    @Test
    public void cache() throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {
        String cacheFilePath = "src/main/resources/product";
        FileUtils.deleteDirectory(new File(OUTPUT_PATH));
        Job job = initJob(Driver.class, DistributedCacheMapper.class, Text.class, NullWritable.class, "/order");
        // 添加缓存文件
        job.addCacheFile(new URI(cacheFilePath));
        boolean result = job.waitForCompletion(true);
        System.exit(result ? 0 : 1);
    }
}
