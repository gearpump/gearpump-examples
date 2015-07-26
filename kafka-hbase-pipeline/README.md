Data flow example that shows reading from a kafka queue and writing to HBase.
Assumes the following
- zookeeper is running and passed to the application as -zookeeper <zookeeper1:port,zookeeper2:port>
- kafka brokers are running and passed to the application as -brokers <broker1:port,broker2:port>
- topic exists and is passed to the application as -topic <topic>
- hbase servers are available on the target node(s)
- hbase table has been created as hbase.table.name

Creating a topic can be done using the scripts/creattopic
Generating data to the topic can be done using the scripts/producetotopic. 

