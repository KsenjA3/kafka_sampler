package org.solutions.jmeter_solution3.consumers;

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.jmeter.testelement.property.JMeterProperty; // Добавьте этот импорт

import java.time.Duration;
import java.util.*;

@Log4j2
public class KafkaConsumerSampler extends AbstractJavaSamplerClient {

    private static final String BOOTSTRAP_SERVERS_PARAM = "BOOTSTRAP_SERVERS";
    private static final String TOPIC_PARAM = "TOPIC_NAME";
    private static final String GROUP_ID_PARAM = "GROUP_ID";
    private static final String POLL_TIMEOUT_MS_PARAM = "POLL_TIMEOUT_MS";
    private static final String SAMPLE_DURATION_MS_PARAM = "SAMPLE_DURATION_MS";
    private static final String SAMPLER_NAME_PARAM = "SAMPLER_NAME";

    private KafkaConsumer<String, String> consumer;
    private String topic;
    private Duration pollTimeout;
    private long sampleDurationMs;
    private String samplerLabel;

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(BOOTSTRAP_SERVERS_PARAM, "localhost:9092");
        defaultParameters.addArgument(TOPIC_PARAM, "jmeter_test_topic");
        defaultParameters.addArgument(GROUP_ID_PARAM, "jmeter_consumer_group_${__threadNum}");
        defaultParameters.addArgument(POLL_TIMEOUT_MS_PARAM, "500");
        defaultParameters.addArgument(SAMPLE_DURATION_MS_PARAM, "5000");
        defaultParameters.addArgument(SAMPLER_NAME_PARAM, "DefaultKafkaConsumer");
        return defaultParameters;
    }

    @Override
    public void setupTest(JavaSamplerContext context) {
        log.info("Вызов setupTest для KafkaConsumerSampler. Инициализация KafkaConsumer.");

        // ИСПРАВЛЕННЫЙ КОД ДЛЯ ЛОГИРОВАНИЯ ПАРАМЕТРОВ
        Map<String, String> paramsMap = new HashMap<>();
        Iterator<String> paramNames = context.getParameterNamesIterator();
        while (paramNames.hasNext()) {
            String paramName = paramNames.next();
            String paramValue = context.getParameter(paramName);
            paramsMap.put(paramName, paramValue);
        }
        log.info("Полученные параметры: " + paramsMap);
        // КОНЕЦ ИСПРАВЛЕННОГО КОДА

        Properties props = new Properties();

        try {
            this.samplerLabel = context.getParameter(SAMPLER_NAME_PARAM);
            String bootstrapServers = context.getParameter(BOOTSTRAP_SERVERS_PARAM);
            this.topic = context.getParameter(TOPIC_PARAM);
            String groupId = context.getParameter(GROUP_ID_PARAM);

            String resolvedPollTimeout = context.getParameter(POLL_TIMEOUT_MS_PARAM);
            try {
                this.pollTimeout = Duration.ofMillis(Long.parseLong(resolvedPollTimeout));
            } catch (NumberFormatException e) {
                log.warn("Value for parameter '" + POLL_TIMEOUT_MS_PARAM + "' not a long: '" + resolvedPollTimeout + "'. Using default: '500'.", e);
                this.pollTimeout = Duration.ofMillis(500);
            }

            String resolvedSampleDuration = context.getParameter(SAMPLE_DURATION_MS_PARAM);
            try {
                this.sampleDurationMs = Long.parseLong(resolvedSampleDuration);
            } catch (NumberFormatException e) {
                log.warn("Value for parameter '" + SAMPLE_DURATION_MS_PARAM + "' not a long: '" + resolvedSampleDuration + "'. Using default: '5000'.", e);
                this.sampleDurationMs = 5000;
            }

            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(topic));
            log.info(this.samplerLabel + ": KafkaConsumer успешно инициализирован. Bootstrap: " + bootstrapServers + ", Topic: " + topic + ", Group ID: " + groupId);

        } catch (Exception e) {
            log.error(this.samplerLabel + ": Ошибка инициализации KafkaConsumer: " + e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать KafkaConsumer. Проверьте настройки и доступность брокеров.", e);
        }
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(this.samplerLabel);
        result.sampleStart();

        long startTime = System.currentTimeMillis();
        long messagesRead = 0;

        try {
            while (System.currentTimeMillis() - startTime < sampleDurationMs) {
                ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
                if (!records.isEmpty()) {
                    messagesRead += records.count();
                    consumer.commitSync();
                }
            }
            result.setSuccessful(true);
            result.setResponseMessage("Successfully consumed " + messagesRead + " messages.");
            result.setResponseData(("Messages read: " + messagesRead).getBytes());
            result.setDataType(SampleResult.TEXT);

        } catch (Exception e) {
            log.error(this.samplerLabel + ": Ошибка при чтении сообщений Kafka: " + e.getMessage(), e);
            result.setSuccessful(false);
            result.setResponseMessage(e.toString());
            result.setResponseData(e.getMessage().getBytes());
        } finally {
            result.sampleEnd();
        }
        return result;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        log.info(this.samplerLabel + ": Вызов teardownTest для KafkaConsumerSampler. Закрытие KafkaConsumer.");
        if (consumer != null) {
            consumer.close();
        }
    }
}