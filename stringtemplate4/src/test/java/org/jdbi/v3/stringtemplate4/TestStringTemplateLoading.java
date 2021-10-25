/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.stringtemplate4;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.internal.exceptions.Unchecked;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.stringtemplate4.TestStringTemplateSqlLocator.Wombat;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestStringTemplateLoading {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withInitializer(TestingInitializers.something()).withPlugin(new SqlObjectPlugin());

    private Handle handle;

    @BeforeEach
    public void setUp() {
        handle = h2Extension.getSharedHandle();
    }

    public void testBaz(int id) {
        Wombat wombat = handle.attach(Wombat.class);
        wombat.insert(new Something(id, "Doo" + id));

        String name = handle.createQuery("select name from something where id = " + id)
                            .mapTo(String.class)
                            .one();

        assertThat(name).isEqualTo("Doo" + id);
    }

    @Test
    public void testConcurrentLoading() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = IntStream
            .range(1, 10)
            .mapToObj(id -> pool.submit(() -> testBaz(id)))
            .collect(Collectors.toList());
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        futures.forEach(Unchecked.consumer(f -> f.get(100, TimeUnit.MILLISECONDS)));
    }
}
