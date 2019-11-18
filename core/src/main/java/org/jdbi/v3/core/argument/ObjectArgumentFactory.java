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
package org.jdbi.v3.core.argument;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.jdbi.v3.core.config.ConfigRegistry;

/**
 * Argument factory that matches a specified type and binds
 * it as an {@link ObjectArgument}.
 */
public class ObjectArgumentFactory implements ArgumentFactory.Preparable {
    /**
     * Match the given type and bind as an object without SQL type information.
     * @param type the Java type to match
     * @return an ArgumentFactory that produces ObjectArguments for values of the supplied type
     */
    public static ArgumentFactory create(Class<?> type) {
        return create(type, null);
    }

    /**
     * Match the given type and bind as an object with the given SQL type information
     * @param type the Java type to match
     * @param sqlType the SQL type to bind
     * @return an ArgumentFactory that produces ObjectArguments for values of the supplied type
     * @see java.sql.Types
     */
    public static ArgumentFactory create(Class<?> type, Integer sqlType) {
        return new ObjectArgumentFactory(type, sqlType);
    }

    private final Class<?> type;
    private final Integer sqlType;

    private ObjectArgumentFactory(Class<?> type, Integer sqlType) {
        this.type = type;
        this.sqlType = sqlType;
    }

    @Override
    public Optional<Function<Object, Argument>> prepare(Type expectedType, ConfigRegistry config) {
        return Optional.of(expectedType)
                .filter(t -> type.equals(t))
                .map(t -> o -> ObjectArgument.of(o, sqlType));
    }

    @Override
    public Collection<? extends Type> prePreparedTypes() {
        return Collections.emptyList();
    }

    @Override
    public Optional<Argument> build(Type expectedType, Object value, ConfigRegistry config) {
        return Objects.equals(type, expectedType) || type.isInstance(value)
                ? Optional.of(ObjectArgument.of(value, sqlType))
                : Optional.empty();
    }
}
