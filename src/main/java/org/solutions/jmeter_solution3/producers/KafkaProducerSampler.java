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

import java.io.Serializable;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit; // Для использования TimeUnit в .get()


@Log4j2
public class KafkaProducerSampler extends AbstractJavaSamplerClient  implements Serializable {

    private static final long serialVersionUID = 1L; // Для корректной сериализации в JMeter
    private transient KafkaProducer<String, String> producer;    // инициализирован один раз и переиспользован потоками, не будет сериализовано, что важно для JMeter.

    // Параметры для Sampler'а  для ключей
    private static final String PARAM_BOOTSTRAP = "bootstrap";
    private static final String PARAM_TOPIC = "topic";
    private static final String PARAM_PAYLOAD = "payloadB";
    private static final String PARAM_ASKS = "acks"; // Добавим ключ сообщения для гибкости
    private static final String PARAM_MESSAGE_KEY = "messageKey"; // Добавим ключ сообщения для гибкости
    private static final String PARAM_TIMEOUT_SECONDS = "send.timeout.seconds";


    // Свойства Kafka Producer, которые будут кэшироваться после setupTest
    private String topicName;
    private int payloadBytes;
    private int sendTimeoutSeconds;

    /**
     * Определяет параметры по умолчанию, которые будут отображаться в GUI JMeter.
     * Облегчает настройку для пользователей.
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments parameters = new Arguments();
        parameters.addArgument(PARAM_BOOTSTRAP, "localhost:9093"); // Адрес вашего Kafka брокера
        parameters.addArgument(PARAM_TOPIC, "load_demo");            // Название темы Kafka
        parameters.addArgument(PARAM_PAYLOAD, "100");                 // Размер сообщения в байтах (повторение символа 'x')
        parameters.addArgument(PARAM_MESSAGE_KEY, "${__threadNum}");      // Встроенная функция JMeter, возвращает порядковый номер текущего потока (виртуального пользователя), который выполняет данный Sampler.
        parameters.addArgument(PARAM_ASKS, "1"); // Уровень подтверждений
        parameters.addArgument(PARAM_TIMEOUT_SECONDS, "15");
        return parameters;
    }
    /**
     * Метод вызывается ОДИН РАЗ при инициализации Sampler'а для каждого экземпляра класса.
     * Используется для инициализации ресурсов, которые будут использоваться всеми потоками.
     *
     * @param context Контекст сэмплера, предоставляющий доступ к параметрам.
     */
    @Override
    public void setupTest(JavaSamplerContext context) {
        log.info("Вызов setupTest для KafkaProducerSampler. Инициализация KafkaProducer.");

        // Получаем параметры из контекста
        String bootstrapServers = context.getParameter(PARAM_BOOTSTRAP);
        topicName = context.getParameter(PARAM_TOPIC);
        payloadBytes = context.getIntParameter(PARAM_PAYLOAD, 50); // 50 - значение по умолчанию, если не указано
        String asks = context.getParameter("acks", "1"); // Уровень подтверждений
        sendTimeoutSeconds = context.getIntParameter(PARAM_TIMEOUT_SECONDS, 15);


        // Настройка свойств Kafka Producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, asks);
//        props.put("schema.registry.url", context.getParameter("schema.registry.url", "http://localhost:8081"));//сериализаторы Avro, Protobuf или JSON Schema

        try {
            // Инициализация KafkaProducer ОДИН РАЗ
            producer = new KafkaProducer<>(props);
            log.info("KafkaProducerSampler: KafkaProducer успешно инициализирован для брокеров: {}", bootstrapServers);
        } catch (Exception e) {
            log.error("KafkaProducerSampler: Ошибка инициализации KafkaProducer: {}", e.getMessage(), e);
            //????? обработка ошибки в случае неудачной инициализации
            throw new RuntimeException("Не удалось инициализировать KafkaProducer. Проверьте настройки и доступность брокеров.", e);
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

        SampleResult res = new SampleResult(); //содержать все данные об одном выполненном запросе (сэмпле)
        res.setSampleLabel("KafkaProducerRequest - Topic: " + topicName); // Для отчетов
        res.sampleStart(); // Запуск таймера для измерения времени выполнения Sampler'а

        String messageKey = context.getParameter(PARAM_MESSAGE_KEY); // Получаем порядковый номер виртуального пользователя

        // Генерируем тело сообщения на каждой итерации
        String messageValue = null;
        StringBuilder sb = new StringBuilder(payloadBytes);
        for (int i = 0; i < payloadBytes; i++) { sb.append('x');  }
        messageValue = sb.toString();

        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topicName, messageKey, messageValue); // Создаем ProducerRecord
            Future<?> future = producer.send(record); // Отправляем сообщение асинхронно

            // Ожидаем завершения отправки сообщения синхронно для точного измерения latency каждого сообщения с таймаутом, чтобы избежать зависаний.
            future.get(15, TimeUnit.SECONDS); //java.util.concurrent.TimeoutException.

            res.setSuccessful(true);
            res.setResponseCodeOK();
            res.setResponseMessage("Сообщение успешно отправлено в Kafka.");
            res.setResponseData(("Key: " + messageKey + ", Value (length): " + messageValue.length()).getBytes()); // отображается на вкладке "Response data"
            res.setDataType(SampleResult.TEXT); //помогает JMeter правильно форматировать и отображать данные в View Results Tree.
            log.debug("KafkaProducerSampler: Сообщение отправлено: Topic={}, Key={}, Value_Length={}", topicName, messageKey, messageValue.length());
        } catch (Exception e) {
            // Обработка ошибок при отправке сообщения
            res.setSuccessful(false);
            res.setResponseCode("500"); // Стандартный код ошибки для внутренних проблем Sampler'а
            res.setResponseMessage("Ошибка при отправке сообщения в Kafka: " + e.getMessage());
            res.setResponseData(e.getMessage().getBytes()); // Данные ответа - текст ошибки
            log.error("KafkaProducerSampler: Ошибка в runTest(): {}", e.getMessage(), e);
        } finally {
            res.sampleEnd(); // Остановка таймера, даже если произошла ошибка
        }
        return res;
    }

    /**
     *  Используется для очистки ресурсов.     *
     * @param context Контекст сэмплера.
     */
    @Override
    public void teardownTest(JavaSamplerContext context) {
        log.info("Вызов teardownTest для KafkaProducerSampler. Закрытие KafkaProducer.");
        if (producer != null) {
            try {
                producer.close(Duration.ofSeconds(5)); // Закрываем продюсер, даем 5 секунд на очистку
                log.info("KafkaProducer успешно закрыт.");
            } catch (Exception e) {
                log.error("Ошибка при закрытии KafkaProducer: " + e.getMessage(), e);
            }
        }
    }
}