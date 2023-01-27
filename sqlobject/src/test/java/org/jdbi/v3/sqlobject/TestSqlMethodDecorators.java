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
package org.jdbi.v3.sqlobject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.extension.HandleSupplier;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.util.Arrays.asList;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSqlMethodDecorators {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withPlugin(new SqlObjectPlugin());

    private Handle testHandle;

    private static final ThreadLocal<List<String>> INVOCATIONS = ThreadLocal.withInitial(ArrayList::new);

    @BeforeEach
    public void setUp() {
        testHandle = h2Extension.getSharedHandle();
        INVOCATIONS.get().clear();
    }

    @Test
    public void testUnordered() {
        Dao dao = testHandle.attach(Dao.class);
        dao.unordered();

        assertThat(INVOCATIONS.get()).isIn(asList("foo", "bar", "method"), asList("bar", "foo", "method"));
    }

    @Test
    public void testOrderedFooBar() {
        Dao dao = testHandle.attach(Dao.class);
        dao.orderedFooBar();

        assertThat(INVOCATIONS.get()).containsExactly("foo", "bar", "method");
    }

    @Test
    public void testOrderedBarFoo() {
        Dao dao = testHandle.attach(Dao.class);
        dao.orderedBarFoo();

        assertThat(INVOCATIONS.get()).containsExactly("bar", "foo", "method");
    }

    @Test
    public void testOrderedFooBarOnType() {
        OrderedOnType dao = testHandle.attach(OrderedOnType.class);
        dao.orderedFooBarOnType();

        assertThat(INVOCATIONS.get()).containsExactly("foo", "bar", "method");
    }

    @Test
    public void testOrderedFooBarOnTypeOverriddenToBarFooOnMethod() {
        OrderedOnType dao = testHandle.attach(OrderedOnType.class);
        dao.orderedBarFooOnMethod();

        assertThat(INVOCATIONS.get()).containsExactly("bar", "foo", "method");
    }

    @Test
    public void testTypeWrapsMethod() {
        TypeDecorator dao = testHandle.attach(TypeDecorator.class);
        dao.typeWrapsMethod();

        assertThat(INVOCATIONS.get()).containsExactly("foo", "bar", "method");
    }

    @Test
    public void testAbortingDecorator() {
        Dao dao = testHandle.attach(Dao.class);
        dao.abortingDecorator();

        assertThat(INVOCATIONS.get()).containsExactly("foo", "abort");
    }

    @Test
    public void testRegisteredDecorator() {
        testHandle.getConfig(HandlerDecorators.class).register(
                (base, sqlObjectType, method) ->
                        (target, args, handleSupplier) -> {
                            invoked("custom");
                            return base.invoke(target, args, handleSupplier);
                        });

        testHandle.attach(Dao.class).orderedFooBar();

        assertThat(INVOCATIONS.get()).containsExactly("custom", "foo", "bar", "method");
    }

    @Test
    public void testRegisteredDecoratorReturnsBase() {
        testHandle.getConfig(HandlerDecorators.class).register((base, sqlObjectType, method) -> base);

        testHandle.attach(Dao.class).orderedFooBar();

        assertThat(INVOCATIONS.get()).containsExactly("foo", "bar", "method");
    }

    static void invoked(String value) {
        INVOCATIONS.get().add(value);
    }

    public interface Dao {
        @Foo
        @Bar
        @CustomSqlOperation
        void unordered();

        @Foo
        @Bar
        @CustomSqlOperation
        @DecoratorOrder({Foo.class, Bar.class})
        void orderedFooBar();

        @Foo
        @Bar
        @CustomSqlOperation
        @DecoratorOrder({Bar.class, Foo.class})
        void orderedBarFoo();

        @Foo
        @Abort
        @Bar
        @CustomSqlOperation
        @DecoratorOrder({Foo.class, Abort.class, Bar.class})
        void abortingDecorator();
    }

    @DecoratorOrder({Foo.class, Bar.class})
    public interface OrderedOnType {
        @Foo
        @Bar
        @CustomSqlOperation
        void orderedFooBarOnType();

        @Foo
        @Bar
        @CustomSqlOperation
        @DecoratorOrder({Bar.class, Foo.class})
        void orderedBarFooOnMethod();
    }

    @Foo
    public interface TypeDecorator {
        @Bar
        @CustomSqlOperation
        void typeWrapsMethod();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @SqlMethodDecoratingAnnotation(Foo.Factory.class)
    public @interface Foo {
        class Factory implements HandlerDecorator {
            @Override
            public Handler decorateHandler(Handler base, Class<?> sqlObjectType, Method method) {
                return (target, args, handleSupplier) -> {
                    invoked("foo");
                    return base.invoke(target, args, handleSupplier);
                };
            }
        }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @SqlMethodDecoratingAnnotation(Bar.Factory.class)
    public @interface Bar {
        class Factory implements HandlerDecorator {
            @Override
            public Handler decorateHandler(Handler base, Class<?> sqlObjectType, Method method) {
                return (target, args, handleSupplier) -> {
                    invoked("bar");
                    return base.invoke(target, args, handleSupplier);
                };
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @SqlMethodDecoratingAnnotation(Abort.Factory.class)
    public @interface Abort {
        class Factory implements HandlerDecorator {
            @Override
            public Handler decorateHandler(Handler base, Class<?> sqlObjectType, Method method) {
                return (target, args, handleSupplier) -> {
                    invoked("abort");
                    return null;
                };
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @SqlOperation(CustomSqlOperation.Impl.class)
    public @interface CustomSqlOperation {
        class Impl implements Handler {
            @Override
            public Object invoke(Object target, Object[] args, HandleSupplier handleSupplier) {
                invoked("method");
                return null;
            }
        }
    }
}
