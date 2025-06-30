package org.solutions.jmeter_solution3.producers;

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.jmeter.testelement.property.JMeterProperty; // Добавьте этот импорт

import java.util.Iterator;
import java.util.Properties;
import java.util.HashMap; // Добавьте этот импорт
import java.util.Map;   // Добавьте этот импорт

@Log4j2
public class KafkaProducerSampler extends AbstractJavaSamplerClient {

    private static final String BOOTSTRAP_SERVERS_PARAM = "BOOTSTRAP_SERVERS";
    private static final String TOPIC_PARAM = "TOPIC_NAME";
    private static final String PAYLOAD_BYTES_PARAM = "PAYLOAD_BYTES";
    private static final String ACKS_PARAM = "ACKS";
    private static final String MESSAGE_KEY_PARAM = "MESSAGE_KEY";
    private static final String SEND_TIMEOUT_SECONDS_PARAM = "SEND_TIMEOUT";
    private static final String SAMPLER_NAME_PARAM = "SAMPLER_NAME";

    private KafkaProducer<String, String> producer;
    private String topic;
    private int payloadBytes;
    private String acks;
    private int sendTimeoutSeconds;
    private String samplerLabel;

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(BOOTSTRAP_SERVERS_PARAM, "localhost:9092");
        defaultParameters.addArgument(TOPIC_PARAM, "jmeter_test_topic");
        defaultParameters.addArgument(PAYLOAD_BYTES_PARAM, "100");
        defaultParameters.addArgument(ACKS_PARAM, "1");
        defaultParameters.addArgument(MESSAGE_KEY_PARAM, "${__threadNum}");
        defaultParameters.addArgument(SEND_TIMEOUT_SECONDS_PARAM, "30");
        defaultParameters.addArgument(SAMPLER_NAME_PARAM, "DefaultKafkaProducer");
        return defaultParameters;
    }

    @Override
    public void setupTest(JavaSamplerContext context) {
        log.info("Вызов setupTest для KafkaProducerSampler. Инициализация KafkaProducer.");

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
            this.acks = context.getParameter(ACKS_PARAM);

            String resolvedPayloadBytes = context.getParameter(PAYLOAD_BYTES_PARAM);
            try {
                this.payloadBytes = Integer.parseInt(resolvedPayloadBytes);
            } catch (NumberFormatException e) {
                log.warn("Value for parameter '" + PAYLOAD_BYTES_PARAM + "' not an integer: '" + resolvedPayloadBytes + "'. Using default: '50'.", e);
                this.payloadBytes = 50;
            }

            String resolvedSendTimeout = context.getParameter(SEND_TIMEOUT_SECONDS_PARAM);
            try {
                this.sendTimeoutSeconds = Integer.parseInt(resolvedSendTimeout);
            } catch (NumberFormatException e) {
                log.warn("Value for parameter '" + SEND_TIMEOUT_SECONDS_PARAM + "' not an integer: '" + resolvedSendTimeout + "'. Using default: '15'.", e);
                this.sendTimeoutSeconds = 15;
            }

            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, this.acks);
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, this.sendTimeoutSeconds * 1000);

            producer = new KafkaProducer<>(props);
            log.info(this.samplerLabel + ": KafkaProducer успешно инициализирован. Bootstrap: " + bootstrapServers + ", Topic: " + topic + ", Acks: " + acks);

        } catch (Exception e) {
            log.error(this.samplerLabel + ": Ошибка инициализации KafkaProducer: " + e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать KafkaProducer. Проверьте настройки и доступность брокеров.", e);
        }
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(this.samplerLabel);
        result.sampleStart();

        try {
            String messageKey = context.getParameter(MESSAGE_KEY_PARAM);
            String message = "Message with size " + payloadBytes + " bytes from thread " + messageKey;

            producer.send(new ProducerRecord<>(topic, messageKey, message)).get();
            result.setSuccessful(true);
            result.setResponseMessage("Message sent successfully");
            result.setResponseData(message.getBytes());
            result.setDataType(SampleResult.TEXT);

        } catch (Exception e) {
            log.error(this.samplerLabel + ": Ошибка при отправке сообщения Kafka: " + e.getMessage(), e);
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
        log.info(this.samplerLabel + ": Вызов teardownTest для KafkaProducerSampler. Закрытие KafkaProducer.");
        if (producer != null) {
            producer.close();
        }
    }
}