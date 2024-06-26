/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.SequentialFileFactoryTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NIOSequentialFileFactoryTest extends SequentialFileFactoryTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected SequentialFileFactory createFactory(String folder) {
      return new NIOSequentialFileFactory(new File(folder), true, 1);
   }

   @Test
   public void testInterrupts() throws Throwable {

      final EncodingSupport fakeEncoding = new EncodingSupport() {
         @Override
         public int getEncodeSize() {
            return 10;
         }

         @Override
         public void encode(ActiveMQBuffer buffer) {
            buffer.writeBytes(new byte[10]);
         }

         @Override
         public void decode(ActiveMQBuffer buffer) {

         }
      };

      final AtomicInteger calls = new AtomicInteger(0);
      final NIOSequentialFileFactory factory = new NIOSequentialFileFactory(new File(getTestDir()), new IOCriticalErrorListener() {
         @Override
         public void onIOException(Throwable code, String message, String file) {
            logger.debug("IOException happening", code);
            calls.incrementAndGet();
         }
      }, 1);

      Thread threadOpen = new Thread() {
         @Override
         public void run() {
            try {
               Thread.currentThread().interrupt();
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadOpen.start();
      threadOpen.join();

      Thread threadClose = new Thread() {
         @Override
         public void run() {
            try {
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
               file.write(fakeEncoding, true);
               Thread.currentThread().interrupt();
               file.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadClose.start();
      threadClose.join();

      Thread threadWrite = new Thread() {
         @Override
         public void run() {
            try {
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
               Thread.currentThread().interrupt();
               file.write(fakeEncoding, true);
               file.close();

            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadWrite.start();
      threadWrite.join();

      Thread threadFill = new Thread() {
         @Override
         public void run() {
            try {
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
               Thread.currentThread().interrupt();
               file.fill(1024);
               file.close();

            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadFill.start();
      threadFill.join();

      Thread threadWriteDirect = new Thread() {
         @Override
         public void run() {
            try {
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
               ByteBuffer buffer = ByteBuffer.allocate(10);
               buffer.put(new byte[10]);
               Thread.currentThread().interrupt();
               file.writeDirect(buffer, true);
               file.close();

            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadWriteDirect.start();
      threadWriteDirect.join();

      Thread threadRead = new Thread() {
         @Override
         public void run() {
            try {
               SequentialFile file = factory.createSequentialFile("file.txt");
               file.open();
               file.write(fakeEncoding, true);
               file.position(0);
               ByteBuffer readBytes = ByteBuffer.allocate(fakeEncoding.getEncodeSize());
               Thread.currentThread().interrupt();
               file.read(readBytes);
               file.close();

            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      threadRead.start();
      threadRead.join();

      // An interrupt exception shouldn't issue a shutdown
      assertEquals(0, calls.get());
   }
}
