package com.framework.log.service;

import com.framework.log.config.LogProperties;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationLogStorageServiceTest {

    @Test
    void constructorRejectsNullProperties() {
        assertThatThrownBy(() -> new OperationLogStorageService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void initCreatesTableOnlyWhenDbStorageEnabledAndMapperPresent() {
        RecordingMapper mapper = new RecordingMapper();
        OperationLogStorageService enabledService = service(true, mapper.proxy());

        enabledService.init();

        assertThat(mapper.createTableCalls).hasValue(1);

        OperationLogStorageService disabledService = service(false, mapper.proxy());
        disabledService.init();

        assertThat(mapper.createTableCalls).hasValue(1);

        OperationLogStorageService missingMapperService = service(true, null);
        assertThatCode(missingMapperService::init).doesNotThrowAnyException();
    }

    @Test
    void dbOperationsDoNotLeakMapperFailuresToBusinessFlow() {
        RecordingMapper mapper = new RecordingMapper();
        mapper.throwOnCreate = true;
        mapper.throwOnInsert = true;
        mapper.throwOnDelete = true;
        OperationLogStorageService service = service(true, mapper.proxy());

        assertThatCode(service::init).doesNotThrowAnyException();
        assertThatCode(() -> service.saveAsync(new OperationLogEntity())).doesNotThrowAnyException();
        assertThatCode(service::cleanExpiredLogs).doesNotThrowAnyException();

        assertThat(mapper.createTableCalls).hasValue(1);
        assertThat(mapper.insertCalls).hasValue(1);
        assertThat(mapper.deleteBeforeCalls).hasValue(1);
    }

    private static OperationLogStorageService service(boolean dbStorageEnabled, OperationLogMapper mapper) {
        LogProperties properties = new LogProperties();
        properties.getDbStorage().setEnabled(dbStorageEnabled);
        OperationLogStorageService service = new OperationLogStorageService(properties);
        if (mapper != null) {
            injectMapper(service, mapper);
        }
        return service;
    }

    private static void injectMapper(OperationLogStorageService service, OperationLogMapper mapper) {
        try {
            Field field = OperationLogStorageService.class.getDeclaredField("operationLogMapper");
            field.setAccessible(true);
            field.set(service, mapper);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static class RecordingMapper {

        private final AtomicInteger createTableCalls = new AtomicInteger();
        private final AtomicInteger insertCalls = new AtomicInteger();
        private final AtomicInteger deleteBeforeCalls = new AtomicInteger();
        private boolean throwOnCreate;
        private boolean throwOnInsert;
        private boolean throwOnDelete;

        private OperationLogMapper proxy() {
            return (OperationLogMapper) Proxy.newProxyInstance(
                    OperationLogMapper.class.getClassLoader(),
                    new Class<?>[]{OperationLogMapper.class},
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "createTableIfNotExists" -> {
                                createTableCalls.incrementAndGet();
                                if (throwOnCreate) {
                                    throw new IllegalStateException("ddl failed");
                                }
                                yield null;
                            }
                            case "insert" -> {
                                insertCalls.incrementAndGet();
                                if (throwOnInsert) {
                                    throw new IllegalStateException("insert failed");
                                }
                                yield null;
                            }
                            case "deleteBefore" -> {
                                deleteBeforeCalls.incrementAndGet();
                                if (throwOnDelete) {
                                    throw new IllegalStateException("delete failed");
                                }
                                yield 1;
                            }
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
