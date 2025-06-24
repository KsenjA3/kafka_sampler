package org.solutions.jmeter_solution3.consumers;

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.kafka.clients.consumer.ConsumerConfig; // Используем ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer; // !!! Правильный десериализатор

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections; // Для Collections.singletonList()
import java.util.Properties;

@Log4j2
public class KafkaConsumerSampler extends AbstractJavaSamplerClient implements Serializable {
    private static final long serialVersionUID = 1L;

    // Параметры для Sampler'а
    private static final String PARAM_BOOTSTRAP = "bootstrap";
    private static final String PARAM_TOPIC = "topic";
    private static final String PARAM_GROUP_ID = "groupId"; // Добавляем group.id
    private static final String PARAM_POLL_TIMEOUT_MS = "pollTimeoutMs"; // Таймаут для каждого poll()
    private static final String PARAM_SAMPLE_DURATION_MS = "sampleDurationMs"; // Длительность одного сэмпла

    // Инициализируется один раз в setupTest и используется всеми потоками, JMeter не сериализует
    private transient KafkaConsumer<String, String> consumer;

    // Свойства Kafka Consumer, которые будут кэшироваться после setupTest
    private String topicName;
    private long pollTimeoutMs;
    private long sampleDurationMs;

    /**
     * Определяет параметры по умолчанию, которые будут отображаться в GUI JMeter.
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments parameters = new Arguments();
        parameters.addArgument(PARAM_BOOTSTRAP, "localhost:9093");
        parameters.addArgument(PARAM_TOPIC, "load_test_topic"); // Должен совпадать с топиком продюсера
        parameters.addArgument(PARAM_GROUP_ID, "jmeter_consumer_group_${__threadNum}"); // Уникальный ID группы для каждого потока
        parameters.addArgument(PARAM_POLL_TIMEOUT_MS, "500"); // Таймаут для каждого вызова poll()
        parameters.addArgument(PARAM_SAMPLE_DURATION_MS, "10000"); // Длительность работы Sampler'а в миллисекундах
        return parameters;
    }

    /**
     * Используется для инициализации ресурсов, которые будут использоваться всеми потоками.
     * @param context Контекст сэмплера, предоставляющий доступ к параметрам.
     */
    @Override
    public void setupTest(JavaSamplerContext context) {
        log.info("Вызов setupTest для KafkaConsumerSampler. Инициализация KafkaConsumer.");

        String bootstrapServers = context.getParameter(PARAM_BOOTSTRAP);
        String groupId = context.getParameter(PARAM_GROUP_ID);
        topicName = context.getParameter(PARAM_TOPIC);
        pollTimeoutMs = context.getLongParameter(PARAM_POLL_TIMEOUT_MS, 500);
        sampleDurationMs = context.getLongParameter(PARAM_SAMPLE_DURATION_MS, 5000);


        // Настройка свойств Kafka Consumer
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // 'earliest' начнет читать с самого начала топика, если нет предыдущего оффсета.
        // 'latest' будет читать только новые сообщения. Выберите в зависимости от задачи.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Отключаем авто-коммит, будем коммитить вручную после обработки.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try {
            consumer = new KafkaConsumer<>(props);
            // Подписываемся на топик один раз в setupTest
            consumer.subscribe(Collections.singletonList(topicName));
            log.info("KafkaConsumerSampler: KafkaConsumer успешно инициализирован и подписан на топик: {} для брокеров: {}, группа: {}",
                    topicName, bootstrapServers, groupId);
        } catch (Exception e) {
            log.error("KafkaConsumerSampler: Ошибка инициализации KafkaConsumer: {}", e.getMessage(), e);
            // Важно выбрасывать RuntimeException, чтобы JMeter пометил setup как неудачный
            throw new RuntimeException("Не удалось инициализировать KafkaConsumer. Проверьте настройки и доступность брокеров.", e);
        }
    }

    /**
     * Основной метод, выполняющий запрос.
     * Вызывается для каждой итерации сэмплера с каждым виртуальным пользователем.
     *
     * @param context Контекст сэмплера, предоставляющий доступ к параметрам и JMeter-функциям.
     * @return SampleResult, содержащий результаты выполнения запроса.
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult res = new SampleResult();
        res.setSampleLabel("KafkaConsumerRequest - Topic: " + topicName);
        res.sampleStart(); // Запуск таймера для измерения времени выполнения Sampler'а

        long messagesReadCount = 0;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + sampleDurationMs;

        try {
            // Цикл опроса сообщений в течение заданного sampleDurationMs
            while (System.currentTimeMillis() < endTime) {
                // Опрашиваем Kafka на наличие новых записей.
                // Duration.ofMillis(pollTimeoutMs) определяет, как долго poll() будет ждать сообщений.
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));

                if (records.isEmpty()) {
                    // Если нет новых сообщений, продолжаем опрос до истечения sampleDurationMs
                    log.debug("KafkaConsumerSampler: Нет новых сообщений.");
                    continue;
                }

                // Обрабатываем полученные записи
                for (ConsumerRecord<String, String> record : records) {
                    messagesReadCount++;
                    log.debug("KafkaConsumerSampler: Получено сообщение: Offset = {}, Partition = {}, Key = {}, Value_Length = {}",
                            record.offset(), record.partition(), record.key(), record.value().length());
                    // Здесь можно добавить логику проверки содержимого сообщения,
                    // например, проверку длины или ключа, если это часть вашего теста.
                }

                // Коммит оффсетов асинхронно для лучшей производительности. Соответствие реальному поведению приложения.
                // Это не блокирует поток и позволяет продолжать опрос.
                consumer.commitAsync((offsets, exception) -> {
                    if (exception != null) {
                        log.error("KafkaConsumerSampler: Ошибка при асинхронной фиксации оффсетов: {}", exception.getMessage(), exception);
                    } else {
                        log.debug("KafkaConsumerSampler: Оффсеты успешно зафиксированы асинхронно: {}", offsets);
                    }
                });
            }

            res.setSuccessful(true);
            res.setResponseCodeOK();
            res.setResponseMessage("Успешно прочитано " + messagesReadCount + " сообщений за " + sampleDurationMs + " мс.");
            res.setResponseData(("Прочитано сообщений: " + messagesReadCount).getBytes());
            res.setDataType(SampleResult.TEXT);
            log.info("KafkaConsumerSampler: Завершение сэмпла. Прочитано {} сообщений за {} мс.", messagesReadCount, (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            res.setSuccessful(false);
            res.setResponseCode("500");
            res.setResponseMessage("Ошибка при чтении сообщений из Kafka: " + e.getMessage());
            res.setResponseData(e.getMessage().getBytes());
            log.error("KafkaConsumerSampler: Ошибка в runTest(): {}", e.getMessage(), e);
        } finally {
            res.sampleEnd(); // Остановка таймера Sampler'а
        }
        return res;
    }

    /**
     * Метод вызывается ОДИН РАЗ по завершении ВСЕХ тестовых потоков.
     * Используется для очистки ресурсов.
     *
     * @param context Контекст сэмплера.
     */
    @Override
    public void teardownTest(JavaSamplerContext context) {
        log.info("Вызов teardownTest для KafkaConsumerSampler. Закрытие KafkaConsumer.");
        if (consumer != null) {
            try {
                // Перед закрытием выполним синхронный коммит, чтобы убедиться,
                // что все обработанные сообщения зафиксированы.
                consumer.commitSync();
                consumer.close(Duration.ofSeconds(5)); // Закрываем потребитель, даем 5 секунд на очистку
                log.info("KafkaConsumer успешно закрыт.");
            } catch (Exception e) {
                log.error("Ошибка при закрытии KafkaConsumer: {}", e.getMessage(), e);
            }
        }
    }
}