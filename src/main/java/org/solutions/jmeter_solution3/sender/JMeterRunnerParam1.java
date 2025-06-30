package org.solutions.jmeter_solution3.sender;

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.report.config.ConfigurationException;
import org.apache.jmeter.report.dashboard.GenerationException;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
// Удалены неиспользуемые импорты: import org.apache.jmeter.testelement.property.JMeterProperty;
// Удалены неиспользуемые импорты: import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class JMeterRunnerParam1 {

    // Установите путь к вашей установке JMeter. Убедитесь, что этот путь верен!
    String jmeterHome = "c:/Users/User/apache-jmeter-5.6.3/"; // Пример: "C:/apache-jmeter-5.6.3/"
    private static final String TEST_PLAN_PATH = "src/main/resources/jmeter_solution3/kafka-sender3param.jmx"; // Убедитесь, что JMX называется kafka-sender3.jmx
    private static final String RESULTS_FILE_PATH = "kafka_test_results3.jtl";
    private static final String DASHBOARD_REPORT_PATH = "kafka_dashboard_report3";
    private static final String GENERATED_JMX_OUTPUT_PATH = "generated_test_plan3.jmx";

    public void runJmeter(Map<String, String> customJMeterProperties) {
        HashTree testPlanTree = null;

        // 1. Инициализация JMeter Environment
        try {
            JMeterUtils.setJMeterHome(jmeterHome);
            JMeterUtils.loadJMeterProperties(jmeterHome + "bin/jmeter.properties");

            // Настройка свойств для отчетов
            JMeterUtils.setProperty("jmeter.reportgenerator.exporter.html.classname", "org.apache.jmeter.report.dashboard.HtmlTemplateExporter");
            JMeterUtils.setProperty("jmeter.save.saveservice.output_format", "csv");
            JMeterUtils.setProperty("jmeter.save.saveservice.default_properties", "true");
            JMeterUtils.setProperty("jmeter.reportgenerator.enabled", "true");
            JMeterUtils.setProperty("jmeter.reportgenerator.overall_result_file", RESULTS_FILE_PATH);
            JMeterUtils.setProperty("jmeter.reportgenerator.output_dir", DASHBOARD_REPORT_PATH);

            // Установка пользовательских свойств JMeter.
            // Эти свойства будут доступны в JMX через ${__P(varName)}
            log.info("Установка пользовательских JMeter свойств:");
            customJMeterProperties.forEach((key, value) -> {
                JMeterUtils.setProperty(key, value);
                log.info("  " + key + " = " + value);
            });

            JMeterUtils.initLocale();
            log.info("JMeter Environment initialized.");
            log.info("Проверка установленных JMeter свойств:");
            customJMeterProperties.forEach((key, value) -> {
                // Получаем свойство так, как его видит JMeter
                String actualValue = JMeterUtils.getProperty(key);
                log.info("  JMeter Property - " + key + " = " + actualValue + " (Ожидалось: " + value + ")");
            });
            // Инициализация SaveService - это необходимо для корректной загрузки JMX файлов
            SaveService.loadProperties();
        } catch (Exception e) {
            log.error("Ошибка при инициализации JMeter Environment: " + e.getMessage(), e);
            return;
        }

        // 2. Загрузка тестового плана
        File in = new File(TEST_PLAN_PATH);
        if (!in.exists()) {
            log.error("Файл тестового плана не найден: " + in.getAbsolutePath());
            return;
        }
        try {
            testPlanTree = SaveService.loadTree(in);
            log.info("Тестовый план успешно загружен из файла: " + in.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при чтении файла тестового плана: " + e.getMessage(), e);
            return;
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при загрузке тестового плана: " + e.getMessage(), e);
            return;
        }

        // 4. Добавление Summarizer и ResultCollector (программный)
        // Эти элементы будут добавлены в корневой узел TestPlan
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        Summariser summariser = !summariserName.isEmpty() ? new Summariser(summariserName) : null;

        ResultCollector resultCollector = new ResultCollector(summariser);
        resultCollector.setFilename(RESULTS_FILE_PATH);
        resultCollector.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.reporters.ResultCollector");
        resultCollector.setName("View Results Tree - from Java");
        resultCollector.setEnabled(true);

        // ResultCollector добавляется к первому элементу корневого узла (обычно это Test Plan)
        if (testPlanTree.getArray().length > 0) {
            testPlanTree.add(testPlanTree.getArray()[0], resultCollector);
            log.info("ResultCollector добавлен к тестовому плану.");
        } else {
            log.warn("Корневой элемент тестового плана не найден. ResultCollector не будет добавлен.");
        }

        // 5. Сохранение JMX-файла для отладки (с внесенными изменениями)
        try (FileOutputStream out = new FileOutputStream(new File(GENERATED_JMX_OUTPUT_PATH))) {
            SaveService.saveTree(testPlanTree, out);
            log.info("Тестовый план (с примененными изменениями) сохранен в: " + GENERATED_JMX_OUTPUT_PATH);
        } catch (Exception e) {
            log.error("Ошибка при сохранении JMX-файла: " + e.getMessage(), e);
        }

        // 6. Конфигурация и запуск JMeter Engine
        StandardJMeterEngine engine = new StandardJMeterEngine();
        engine.configure(testPlanTree); // Передаем модифицированный тестовый план

        try {
            log.info("Запуск JMeter теста...");
            engine.run();
            log.info("JMeter тест завершен. Результаты в: " + RESULTS_FILE_PATH);
        } catch (Exception e) {
            log.error("Ошибка при выполнении JMeter теста:", e);
        } finally {
            engine.exit();
        }

        // 7. Генерация HTML-отчета
//        try {
//            log.info("Начало генерации HTML-отчета...");
//            // null означает, что ReportGenerator будет использовать свойство jmeter.reportgenerator.output_dir
//            ReportGenerator reportGen = new ReportGenerator(RESULTS_FILE_PATH, null);
//            reportGen.generate();
//            log.info("HTML-отчет успешно сгенерирован в папке: " + DASHBOARD_REPORT_PATH);
//        } catch (GenerationException | ConfigurationException e) {
//            log.error("Ошибка при генерации отчета: " + e.getMessage(), e);
//        }
    }

    public static void main(String[] args) {
        JMeterRunnerParam1 runner = new JMeterRunnerParam1();
        HashMap<String, String> testParameters = new HashMap<>();

        // *** Параметры, которые будут подаваться в JMeter ***

        // Параметры для Kafka Consumer/Producer Samplers (доступны как JMeter Properties через ${__P(NAME)})
        testParameters.put("BOOTSTRAP_SERVERS", "localhost:9092"); // Адрес Kafka брокера
        testParameters.put("TOPIC_NAME", "my_test_topic");         // Имя топика Kafka
        testParameters.put("PAYLOAD_BYTES", "100");                // Размер сообщения для Producer (если используется)
        testParameters.put("ACKS", "1");                           // Уровень подтверждения для Producer (0, 1, all, -1)
        testParameters.put("SEND_TIMEOUT", "30");                  // Таймаут отправки для Producer в секундах

        testParameters.put("POLL_TIMEOUT_MS", "500");              // Таймаут опроса для Consumer в мс
        testParameters.put("SAMPLE_DURATION_MS", "3000");          // Длительность сэмплирования для Consumer в мс

        // Параметры для Thread Group (будут установлены напрямую в Java коде)
        testParameters.put("THREADS", "10");         // Количество пользователей (потоков)
        testParameters.put("RAMP_UP_PERIOD", "5");   // Время наращивания в секундах
        testParameters.put("LOOPS", "1");            // Количество итераций
        testParameters.put("DURATION_SECONDS", "60"); // Длительность ThreadGroup для Consumers, если scheduler=true

        // Уровни логирования для ваших Sampler'ов
        testParameters.put("log_level.org.solutions.jmeter_solution3.producers", "DEBUG");
        testParameters.put("log_level.org.solutions.jmeter_solution3.consumers", "DEBUG");

        runner.runJmeter(testParameters);
    }
}