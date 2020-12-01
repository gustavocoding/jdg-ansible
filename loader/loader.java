////usr/bin/env jbang "$0" "$@" ; exit $? # (1)
//FILES words.txt
//DEPS org.infinispan:infinispan-client-hotrod:9.4.21.Final 
//JAVA_OPTIONS -Xmx2g

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.io.*;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.UTF8StringMarshaller;

class loader {

   public static void main(String[] args) throws Exception {
      String USAGE = "\nUsage: load.sh --entries num [--server host:port] [--cache name] [--write-batch num] [--phrase-size num] [--protocol version] [--security]\n";

      Runnable usage = () -> {
         System.out.println(USAGE);
      };

      if (args.length == 0 || args.length % 2 != 0) {
         usage.run();
         return;
      }

      Map<String, String> options = new HashMap<>();
      for (int i = 0; i < args.length; i = i + 2) {
         String option = args[i];
         if (!option.startsWith("--")) {
            usage.run();
            return;
         }
         options.put(option.substring(2), args[i + 1]);
      }
      int entries = 0;

      String entriesValue = options.get("entries");
      String writeBatchValue = options.get("write-batch");
      String phraseSizeValue = options.get("phrase-size");
      String securityValue = options.get("security");
      String serverValue = options.get("server");
      String protocolValue = options.get("protocol");
      String cacheValue = options.get("cache");

      String host = "localhost";
      int port = 11222;
      if (serverValue != null) {
         int sep = serverValue.indexOf(':');
         if (sep != -1) {
            host = serverValue.substring(0, sep);
            port = Integer.parseInt(serverValue.substring(sep + 1));
         } else {
            host = serverValue;
         }
      }

      final int phrase_size = phraseSizeValue != null ? Integer.parseInt(phraseSizeValue) : 10;
      final int write_batch = writeBatchValue != null ? Integer.parseInt(writeBatchValue) : 10000;
      final boolean security = securityValue != null ? Boolean.valueOf(securityValue) : false;
      final String cacheName = cacheValue != null ? cacheValue : "default";
      if (entriesValue == null) {
         System.out.println("option 'entries' is required");
         usage.run();
         return;
      } else {
         entries = Integer.parseInt(entriesValue);
      }

      System.out.println(String.format("Loading %d entries with write batch size of %d and phrase size of %d\n", entries, write_batch, phrase_size));

      List<String> wordList = new ArrayList<>();
      InputStream inputStream = ClassLoader.getSystemClassLoader().getSystemResourceAsStream("words.txt");
      InputStreamReader streamReader = new InputStreamReader(inputStream, "UTF-8");
      BufferedReader in = new BufferedReader(streamReader);

      for (String line; (line = in.readLine()) != null; ) {
         wordList.add(line);
      }

      int sz = wordList.size();

      Random rand = new Random();

      Supplier<String> randomWord = () -> wordList.get(rand.nextInt(sz));

      Supplier<String> randomPhrase = () -> IntStream.range(0, phrase_size).boxed().map(i -> randomWord.get()).collect(Collectors.joining(" "));

      ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
      clientBuilder.addServer().host(host).port(port);
      if (security) {
         clientBuilder.security().authentication().enable().username("user").password("user");
      }
      if (protocolValue != null) {
         clientBuilder.protocolVersion(protocolValue);
      }
      clientBuilder.marshaller(new UTF8StringMarshaller());
      RemoteCacheManager rcm = new RemoteCacheManager(clientBuilder.build());
      RemoteCache<String, String> cache = rcm.getCache(cacheName);
      cache.clear();

      Map<String, String> group = new HashMap<>();
      for (int i = 1; i <= entries; i++) {
         group.put(String.valueOf(i), randomPhrase.get());
         if (group.size() == write_batch) {
            cache.putAll(group);
            System.out.print(String.format("Loaded %s entries\r", i));
            group = new HashMap<>();
         }
      }
      cache.putAll(group);
      System.out.println("\n");
   }

}
