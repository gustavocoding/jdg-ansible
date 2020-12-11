////usr/bin/env jbang "$0" "$@" ; exit $? # (1)
//DEPS org.openjdk.jmh:jmh-core:1.26
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.26
//DEPS org.infinispan:infinispan-client-hotrod:9.4.21.Final
//DEPS info.picocli:picocli:4.5.0
//SOURCES loader.java
//FILES words.txt
//JAVA_OPTIONS -Xmx10g

package org.infinispan;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.UTF8StringMarshaller;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xmx20G"})
@Command(name = "Stress", mixinStandardHelpOptions = true, version = "stress 0.1", description = "Simple stress test for JDG.")
@SuppressWarnings("unused")
public class stress implements Callable<Void> {
   public static final String DEFAULT_PROTOCOL = "2.5";
   public static final String DEFAULT_CACHE = "default";
   public static final String DEFAULT_SERVER = "127.0.0.1:11222";
   public static final String DEFAULT_THREADS = "10";
   public static final String DEFAULT_DURATION_MIN = "1";

   @Option(names = "-t", description = "The number of threads.", defaultValue = DEFAULT_THREADS)
   private int threads;

   @Option(names = "-d", description = "The duration in minutes.", defaultValue = DEFAULT_DURATION_MIN)
   private int durationMin;

   @Option(names = "-c", description = "The cache name.", defaultValue = DEFAULT_CACHE)
   @Param(value = DEFAULT_CACHE)
   private String cacheName;

   @Option(names = "-p", description = "HotRod protocol version.", defaultValue = DEFAULT_PROTOCOL)
   @Param(value = DEFAULT_PROTOCOL)
   private String protocol;

   @Option(names = "-s", description = "Server host and port", defaultValue = DEFAULT_SERVER)
   @Param(value = DEFAULT_SERVER)
   private String server;

   @Option(names = "-w", description = "Write percentage", required = true)
   @Param(value = "0")
   private String writePercent;

   @Option(names = "-g", description = "Get percentage", required = true)
   @Param(value = "0")
   private String getPercent;

   @Option(names = "-r", description = "Remove percentage", required = true)
   @Param(value = "0")
   private String removePercent;

   private static final Random RANDOM = new Random();

   RemoteCache<String, String> cache;
   Helper helper;

   public static void main(String[] args) {
      int exitCode = new CommandLine(new stress()).execute(args);
      System.exit(exitCode);
   }

   @Override
   public Void call() throws Exception {
      Options opt = new OptionsBuilder()
            .include(stress.class.getSimpleName())
            .measurementIterations(1)
            .threads(threads)
            .warmupIterations(0)
            .param("protocol", protocol)
            .param("server", server)
            .param("cacheName", cacheName)
            .param("writePercent", writePercent)
            .param("getPercent", getPercent)
            .param("removePercent", removePercent)
            .measurementTime(new TimeValue(durationMin, TimeUnit.MINUTES))
            .output(String.format("result-%d.txt", System.currentTimeMillis()))
            .build();
      new Runner(opt).run();
      return null;
   }

   @Setup
   public void setup() {
      ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
      clientBuilder.addServers(server + ";");
      clientBuilder.version(ProtocolVersion.PROTOCOL_VERSION_25);
      clientBuilder.marshaller(new UTF8StringMarshaller());
      clientBuilder.forceReturnValues(true);
      RemoteCacheManager rcm = new RemoteCacheManager(clientBuilder.build());
      cache = rcm.getCache(cacheName);
      helper = new Helper(writePercent, getPercent, removePercent, cache.size());
   }

   @TearDown
   public void printExtraInfo() {
      System.out.printf("Gets=%d, Empty Gets=%d, Puts=%d, Remove existing=%d, Remove unexistent=%d%n%n",
            helper.gets.longValue(), helper.emptyGets.longValue(), helper.puts.longValue(), helper.removes.longValue(), helper.emptyRemoves.longValue());
   }

   @Benchmark
   @BenchmarkMode({Mode.SampleTime})
   public void loadGenerator(Blackhole blackhole) {
      switch (helper.generateOp()) {
         case PUT:
            String newId = helper.generateKeyForInserting();
            blackhole.consume(cache.put(newId, org.infinispan.loader.randomPhrase(10)));
            helper.entryInserted(newId);
            break;
         case REMOVE:
            String keyToRemove = helper.generateKeyForDeleting();
            String returnValue = cache.remove(keyToRemove);
            helper.entryDeleted(keyToRemove, returnValue);
            break;
         default:
            String keyToRead = helper.generateKeyForReading();
            String ret = cache.get(keyToRead);
            helper.entryRead(keyToRead, ret);
            break;
      }
   }

   static final class Helper {
      enum Operation {PUT, GET, REMOVE}

      private final Random random = new Random(0);
      private final int writePercent;
      private final int readPercent;
      private final LongAdder puts = new LongAdder();
      private final LongAdder gets = new LongAdder();
      private final LongAdder emptyGets = new LongAdder();
      private final LongAdder removes = new LongAdder();
      private final LongAdder emptyRemoves = new LongAdder();
      private final Queue<String> usedKeys = new ConcurrentLinkedQueue<>();
      private final Queue<String> deletedKeys = new ConcurrentLinkedQueue<>();
      private final AtomicInteger counter;
      private static final String INVALID_KEY = "-1";

      public Helper(String writePct, String getPct, String removePct, int size) {
         int wp = Integer.parseInt(writePct);
         int gp = Integer.parseInt(getPct);
         int rp = Integer.parseInt(removePct);

         if (wp + gp + rp != 100) throw new RuntimeException("Invalid Percentages");

         this.writePercent = wp;
         this.readPercent = gp;
         List<String> ids = IntStream.range(1, size).boxed().map(String::valueOf).collect(Collectors.toList());
         Collections.shuffle(ids, random);
         usedKeys.addAll(ids);
         counter = new AtomicInteger(ids.size());
      }

      void entryInserted(String key) {
         puts.increment();
         usedKeys.add(key);
      }

      void entryDeleted(String key, Object returnValue) {
         deletedKeys.add(key);
         usedKeys.remove(key);
         if (returnValue == null) {
            emptyRemoves.increment();
         } else {
            removes.increment();
         }
      }

      public void entryRead(String keyToRead, Object returnValue) {
         if (returnValue == null) {
            emptyGets.increment();
         } else {
            gets.increment();
         }
      }

      String generateKeyForReading() {
         String key = usedKeys.poll();
         if (key != null) {
            usedKeys.add(key);
            return key;
         }
         return INVALID_KEY;
      }

      String generateKeyForDeleting() {
         String poll = usedKeys.poll();
         if (poll != null) return poll;
         return INVALID_KEY;
      }

      String generateKeyForInserting() {
         String key = deletedKeys.poll();
         if (key != null) {
            return key;
         }
         return String.valueOf(counter.incrementAndGet());
      }

      Operation generateOp() {
         int i = random.nextInt(100);
         if (i < writePercent) return Operation.PUT;
         if (i < readPercent + writePercent) return Operation.GET;
         return Operation.REMOVE;
      }
   }
}


