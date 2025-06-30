package org.solutions.jmeter_solution3.sender;

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class JMeterRunner {
}
package org.solutions.jmeter_solution3.sender;


import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.report.config.ConfigurationException;
import org.apache.jmeter.report.dashboard.GenerationException;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

@Log4j2
public class JMeterRunner {

    // Установите путь к вашей установке JMeter
    String jmeterHome = "c:/Users/User/apache-jmeter-5.6.3/";
    private static final String TEST_PLAN_PATH = "kafka-sender3.jmx";
    private static final String RESULTS_FILE_PATH = "kafka_test_results3.jtl";
    private static final String DASHBOARD_REPORT_PATH = "kafka_dashboard_report3";

    public void runJmeter(HashMap<String, String> properties) {
        HashTree testPlanTree = null;


        // 1. Инициализация JMeter Environment
        try {
//            System.out.println( Class.forName("org.solutions.jmeter_solution3.consumers.KafkaConsumerSampler"));
//            System.out.println(Class.forName("org.solutions.jmeter_solution3.producers.KafkaProducerSampler"));

            JMeterUtils.setJMeterHome(jmeterHome);
            // Загрузка свойств JMeter
            JMeterUtils.loadJMeterProperties(jmeterHome + "bin/jmeter.properties");
            JMeterUtils.setProperty("jmeter.reportgenerator.exporter.html.classname",
                    "org.apache.jmeter.report.dashboard.HtmlTemplateExporter");
            JMeterUtils.setProperty("jmeter.save.saveservice.output_format", "csv"); // Отчеты требуют CSV
            JMeterUtils.setProperty("jmeter.save.saveservice.default_properties", "true"); // Сохранять все поля для отчета
            JMeterUtils.setProperty("jmeter.reportgenerator.enabled", "true"); // Включить генератор отчетов
            JMeterUtils.setProperty("jmeter.reportgenerator.overall_result_file", RESULTS_FILE_PATH); // Указываем JTL
            JMeterUtils.setProperty("jmeter.reportgenerator.output_dir", DASHBOARD_REPORT_PATH); // Куда генерировать

            properties.forEach(JMeterUtils::setProperty);
            JMeterUtils.initLocale(); // Инициализация локали
            log.info("JMeter Environment initialized.");

            // Инициализация SaveService - это необходимо для корректной загрузки JMX файлов
            // (он загружает информацию о том, как десериализовать элементы JMeter)
            SaveService.loadProperties();
        } catch (Exception e) {
            log.error("Ошибка при инициализации JMeter Environment: " + e.getMessage());
            e.printStackTrace();
            return; // Завершаем выполнение, если инициализация не удалась
        }

        // 2. Загрузка тестового плана
        File in = new File("src/main/resources/jmeter_solution3/kafka-sender3.jmx");
        try {
            testPlanTree = SaveService.loadTree(in);
            log.info("Тестовый план успешно загружен из файла: " + in.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при чтении файла тестового плана: " + e.getMessage());
            e.printStackTrace();
            return; // Завершаем выполнение метода, если не удалось загрузить тестовый план
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при загрузке тестового плана: " + e.getMessage());
            e.printStackTrace();
            return; // Завершаем выполнение метода при любой другой ошибке
        }

        // 3. Add Summarizer and ResultCollector
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        Summariser summariser = !summariserName.isEmpty() ? new Summariser(summariserName) : null;

        // Output file
        ResultCollector resultCollector = new ResultCollector(summariser);
        resultCollector.setFilename(RESULTS_FILE_PATH);
        resultCollector.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.visualizers.ViewResultsFullVisualizer");
        testPlanTree.add(testPlanTree.getArray()[0], resultCollector);  //обеспечивает, что ResultCollector будет собирать данные для всего тестового плана

        // 4 Сохранение JMX-файла для отладки
        String jmxOutputFile = "generated_test_plan3.jmx";
        try (FileOutputStream out = new FileOutputStream(jmxOutputFile)) {
            SaveService.saveTree(testPlanTree, out);
            log.info("Тестовый план сохранен в: " + jmxOutputFile);
        } catch (Exception e) {
            log.error("Ошибка при сохранении JMX-файла: " + e.getMessage());
            e.printStackTrace();
        }

        // 5. Конфигурация и запуск JMeter Engine
        StandardJMeterEngine engine = new StandardJMeterEngine();
        engine.configure(testPlanTree); // Передаем собранный тестовый план

        try {
            log.info("Запуск JMeter теста...");
            engine.run(); // Запуск теста
            log.info("JMeter тест завершен. Результаты в: " + RESULTS_FILE_PATH);
        } catch (Exception e) {
            log.error("Ошибка при выполнении JMeter теста:");
            e.printStackTrace();
        } finally {
            // Завершение работы JMeter (очистка ресурсов)
            engine.exit();
        }

        // 6. Generate HTML Report
//        try {
//            ReportGenerator reportGen = new ReportGenerator(RESULTS_FILE_PATH, null);
//            log.info("Начало генерации HTML-отчета...");
//            reportGen.generate();
//            log.info("HTML-отчет успешно сгенерирован.");
//        } catch (GenerationException | ConfigurationException e) {
//            log.error("Ошибка при генерации отчета: " + e.getMessage());
//            e.printStackTrace();
//        }
//                System.out.println("HTML Report generated in folder: ./report-output");

    }

    public static void main(String[] args) {
        JMeterRunner runner = new JMeterRunner();
        HashMap<String, String> customProperties = new HashMap<>();

        // Пример пользовательских свойств JMeter, которые можно передать:
        // Эти свойства будут доступны в JMX файле через ${__P(varName)}
        customProperties.put("THREADS", "20"); // Устанавливаем значение для переменной THREADS в JMX

        // Уровень логирования для ваших Sampler'ов
        customProperties.put("log_level.org.solutions.jmeter_solution3.producers", "DEBUG");
        customProperties.put("log_level.org.solutions.jmeter_solution3.consumers", "DEBUG");

        // Если хотите переопределить адрес Kafka из JMX (если он там есть как переменная)
        // customProperties.put("kafka.bootstrap.servers", "another.kafka:9092");

        runner.runJmeter(customProperties);
    }
}
