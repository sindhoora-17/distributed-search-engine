[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/JRxkKq8u)
## CSC 435 Programming Assignment 5 (Spring 2025)
**Jarvis College of Computing and Digital Media - DePaul University**

**Student**: Naga Sai Lakshmi Sindhoora Rajasekharuni (nrajasek@depaul.edu)  
**Solution programming language**: Java

**Directory Structure**

```
csc-435-pa5-sindhoora-17/
├── app-java/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── csc435/
│   │   │   │       └── app/
│   │   │   │           ├── ClientProcessingEngine.java
│   │   │   │           ├── FileRetrievalClient.java
│   │   │   │           ├── FileRetrievalServer.java
│   │   │   │           ├── FileRetrievalBenchmark.java
│   │   │   │           ├── ServerProcessingEngine.java
│   │   │   │           └── IndexStore.java
│   │   │   └── proto/
│   │   │       └── retrieval.proto
├── README.md
```


### Requirements

If you are implementing your solution in Java you will need to have Java 21.x and Maven 3.8.x installed on your systems. On Ubuntu 24.04 LTS you can install Java and Maven using the following commands:

```
sudo apt install openjdk-21-jdk maven
```

### Setup

There are 3 datasets (dataset1_client_server, dataset2_client_server, dataset3_client_server) that you need to use to evaluate the indexing performance of your solution.
Before you can evaluate your solution you need to download the datasets. You can download the datasets from the following link:

https://depauledu-my.sharepoint.com/:f:/g/personal/aorhean_depaul_edu/Ej4obLnAKMdFh1Hidzd1t1oBHY7IvgqXoLdKRg-buoiisw?e=SWLALa

After you finished downloading the datasets copy them to the dataset directory (create the directory if it does not exist).
Here is an example on how you can copy Dataset1 to the remote machine and how to unzip the dataset:

```
remote-computer$ mkdir datasets
local-computer$ scp dataset1_client_server.zip cc@<remote-ip>:<path-to-repo>/datasets/.
remote-computer$ cd <path-to-repo>/datasets
remote-computer$ unzip dataset1_client_server.zip
```

### Java solution
#### How to build/compile

To build the Java solution use the following commands:
```
cd app-java
mvn compile
mvn package
```

#### How to run application

To run the Java server (after you build the project) use the following command:
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalServer <port>
> <quit>
```

To run the Java client (after you build the project) use the following command:
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalClient
> <connect | get_info | index | search | quit>
```

To run the Java benchmark (after you build the project) use the following command:
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalBenchmark <server IP> <server port> <number of clients> [<dataset path>]
```

To fix OutOfMemoryError while running the server, use the following command:
```
java -Xmx2G -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalServer <port>
```

#### Example (2 clients and 1 server)

**Step 1:** start the server:

Server
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalServer 12345
>
```

**Step 2:** start the clients and connect them to the server:

Client 1
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalClient
> connect 127.0.0.1 12345
Connection successful!
> get_info
Client ID: 1
```

Client 2
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalClient
> connect 127.0.0.1 12345
Connection successful!
> get_info
Client ID: 2
```

**Step 3:** index files from the clients:

Client 1
```
> index ../datasets/dataset1_client_server/2_clients/client_1
Completed indexing 68383239 bytes of data
Completed indexing in 2.974 seconds
```

Client 2
```
> index ../datasets/dataset1_client_server/2_clients/client_2
Completed indexing 65864138 bytes of data
Completed indexing in 2.386 seconds
```

**Step 4:** search files from the clients:

Client 1
```
> search the
Search completed in 0.4 seconds
Search results (top 10 out of 0):
> search child-like
Search completed in 2.1 seconds
Search results (top 10 out of 15):
* client 2:folder7/Document10926.txt:4
* client 1:folder3/Document10379.txt:3
* client 2:folder6/Document10866.txt:2
* client 2:folder8/Document1108.txt:1
* client 2:folder7/folderD/Document11050.txt:1
* client 2:folder6/Document10848.txt:1
* client 2:folder6/Document1082.txt:1
* client 1:folder4/Document10681.txt:1
* client 1:folder4/Document10669.txt:1
* client 1:folder3/Document10387.txt:1
> search distortion AND adaptation
Search completed in 3.27 seconds
Search results (top 10 out of 4):
* client 2:folder7/folderC/Document10998.txt:6
* client 1:folder4/Document10516.txt:3
* client 2:folder8/Document11159.txt:2
* client 2:folder8/Document11157.txt:2
>
```

Client 2
```
> search vortex
Search completed in 2.8 seconds
Search results (top 10 out of 27):
* client 2:folder5/folderB/Document10706.txt:6
* client 2:folder5/folderB/Document10705.txt:4
* client 2:folder7/Document1091.txt:3
* client 1:folder4/Document10681.txt:3
* client 2:folder6/Document1082.txt:2
* client 1:folder4/Document1051.txt:2
* client 1:folder3/folderA/Document10422.txt:2
* client 1:folder2/Document1033.txt:2
* client 2:folder8/Document11159.txt:1
* client 2:folder8/Document11154.txt:1
> search moon AND vortex
Search completed in 3.8 seconds
Search results (top 10 out of 19):
* client 2:folder5/folderB/Document10706.txt:26
* client 1:folder4/Document10681.txt:19
* client 1:folder3/Document1043.txt:19
* client 1:folder4/Document10600.txt:17
* client 2:folder8/Document11154.txt:13
* client 1:folder3/folderA/Document10422.txt:6
* client 1:folder3/Document10379.txt:6
* client 1:folder3/folderA/Document10421.txt:6
* client 2:folder5/folderB/Document10705.txt:5
* client 1:folder4/Document1033.txt:5
>
```

**Step 5:** close and disconnect the clients:

Client 1
```
> quit
```

Client 2
```
> quit
```

**Step 6:** close the server:

Server
```
> quit
```

#### Example (benchmark with 2 clients and 1 server)

**Step 1:** start the server:

Server
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalServer 12345
>
```

**Step 2:** start the benchmark:

Benchmark
```
java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalBenchmark 127.0.0.1 12345 2 ../datasets/dataset1_client_server/2_clients/client_1 ../datasets/dataset1_client_server/2_clients/client_2
Completed indexing 134247377 bytes of data
Completed indexing in 6.015 seconds
Searching search the
Search completed in 0.4 seconds
Search results (top 10 out of 0):
Searching search child-like
Search completed in 2.1 seconds
Search results (top 10 out of 15):
* client 2:folder7/Document10926.txt:4
* client 1:folder3/Document10379.txt:3
* client 2:folder6/Document10866.txt:2
* client 2:folder8/Document1108.txt:1
* client 2:folder7/folderD/Document11050.txt:1
* client 2:folder6/Document10848.txt:1
* client 2:folder6/Document1082.txt:1
* client 1:folder4/Document10681.txt:1
* client 1:folder4/Document10669.txt:1
* client 1:folder3/Document10387.txt:1
Searching search vortex
Search completed in 2.8 seconds
Search results (top 10 out of 27):
* client 2:folder5/folderB/Document10706.txt:6
* client 2:folder5/folderB/Document10705.txt:4
* client 2:folder7/Document1091.txt:3
* client 1:folder4/Document10681.txt:3
* client 2:folder6/Document1082.txt:2
* client 1:folder4/Document1051.txt:2
* client 1:folder3/folderA/Document10422.txt:2
* client 1:folder2/Document1033.txt:2
* client 2:folder8/Document11159.txt:1
* client 2:folder8/Document11154.txt:1
Searching search moon AND vortex
Search completed in 3.8 seconds
Search results (top 10 out of 19):
* client 2:folder5/folderB/Document10706.txt:26
* client 1:folder4/Document10681.txt:19
* client 1:folder3/Document1043.txt:19
* client 1:folder4/Document10600.txt:17
* client 2:folder8/Document11154.txt:13
* client 1:folder3/folderA/Document10422.txt:6
* client 1:folder3/Document10379.txt:6
* client 1:folder3/folderA/Document10421.txt:6
* client 2:folder5/folderB/Document10705.txt:5
* client 1:folder4/Document1033.txt:5
Searching search distortion AND adaptation
Search completed in 3.27 seconds
Search results (top 10 out of 4):
* client 2:folder7/folderC/Document10998.txt:6
* client 1:folder4/Document10516.txt:3
* client 2:folder8/Document11159.txt:2
* client 2:folder8/Document11157.txt:2
```

**Step 3:** close the server:

Server
```
> quit
```
