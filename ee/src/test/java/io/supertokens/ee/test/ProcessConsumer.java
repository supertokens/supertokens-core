package io.supertokens.ee.test;

@FunctionalInterface
public interface ProcessConsumer {
    void accept(TestingProcessManager.TestingProcess testingProcess) throws Exception;
}
