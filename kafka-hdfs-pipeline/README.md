Data flow example that shows reading from a kafka queue and writing to a parquet file which is then copied to HDFS
Assumes the following
- zookeeper is running and passed to the application as -zookeeper <zookeeper1:port,zookeeper2:port>
- kafka brokers are running and passed to the application as -brokers <broker1:port,broker2:port>
- topic exists and is passed to the application as -topic <topic>

Creating a topic can be done using the scripts/creattopic
Generating data to the topic can be done using the scripts/producetotopic. This will read data from the file test.csv.

