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
import org.apache.jmeter.testelement.property.JMeterProperty; // Добавлено
import org.apache.jmeter.testelement.property.StringProperty; // Добавлено
import org.apache.jmeter.threads.ThreadGroup; // Добавлено
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass; // Добавлено

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection; // Добавлено
import java.util.HashMap;
import java.util.Map; // Добавлено

@Log4j2
public class JMeterRunnerParam {

    // Установите путь к вашей установке JMeter
    String jmeterHome = "c:/Users/User/apache-jmeter-5.6.3/";
    private static final String TEST_PLAN_PATH = "src/main/resources/jmeter_solution3/kafka-sender3param.jmx";
    private static final String RESULTS_FILE_PATH = "kafka_test_results3.jtl";
    private static final String DASHBOARD_REPORT_PATH = "kafka_dashboard_report3";
    private static final String GENERATED_JMX_OUTPUT_PATH = "generated_test_plan3.jmx"; // Имя выходного JMX

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

            // Установка пользовательских свойств JMeter
            // Эти свойства будут доступны в JMX через ${__P(varName, defaultValue)} или ${varName}
            // если они определены как User Defined Variables.
            // Примечание: Для Sampler'ов, которые используют свойства, ${VAR_NAME} работает напрямую.
            log.info("Установка пользовательских JMeter свойств:");
            customJMeterProperties.forEach((key, value) -> {
                JMeterUtils.setProperty(key, value);
                log.info("  " + key + " = " + value);
            });

            JMeterUtils.initLocale();
            log.info("JMeter Environment initialized with properties: " + customJMeterProperties);

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


        // 3. Динамическая модификация элементов Test Plan (особенно Thread Group)
        // Этот шаг выполняется ПОСЛЕ загрузки JMX, но ДО конфигурации движка
        SearchByClass<ThreadGroup> threadGroupsSearcher = new SearchByClass<>(ThreadGroup.class);
        testPlanTree.traverse(threadGroupsSearcher);
        Collection<ThreadGroup> threadGroups = threadGroupsSearcher.getSearchResults();

        if (threadGroups.isEmpty()) {
            log.warn("В тестовом плане не найдено ни одной Thread Group.");
        } else {
            log.info("Найдено " + threadGroups.size() + " Thread Group(s). Попытка модификации...");
            for (ThreadGroup tg : threadGroups) {
                log.info("Обработка ThreadGroup: " + tg.getName());

                // Установка количества потоков
                if (customJMeterProperties.containsKey("THREADS")) {
                    try {
                        int numThreads = Integer.parseInt(customJMeterProperties.get("THREADS"));
                        tg.setNumThreads(numThreads);
                        log.info("  Установлено количество потоков для '" + tg.getName() + "': " + numThreads);
                    } catch (NumberFormatException e) {
                        log.error("  Неверный формат для параметра 'THREADS': " + customJMeterProperties.get("THREADS"), e);
                    }
                }

                // Установка периода разгона
                if (customJMeterProperties.containsKey("RAMP_UP_PERIOD")) {
                    try {
                        int rampUp = Integer.parseInt(customJMeterProperties.get("RAMP_UP_PERIOD"));
                        tg.setRampUp(rampUp);
                        log.info("  Установлен период разгона для '" + tg.getName() + "': " + rampUp + " секунд.");
                    } catch (NumberFormatException e) {
                        log.error("  Неверный формат для параметра 'RAMP_UP_PERIOD': " + customJMeterProperties.get("RAMP_UP_PERIOD"), e);
                    }
                }

                // Установка количества итераций
                if (customJMeterProperties.containsKey("LOOPS")) {
                    try {
                        int loops = Integer.parseInt(customJMeterProperties.get("LOOPS"));
                        // LoopCount хранится внутри LoopController, который является SamplerController для ThreadGroup
                        if (tg.getSamplerController() instanceof LoopController) {
                            LoopController loopController = (LoopController) tg.getSamplerController();
                            loopController.setLoops(loops);
                            loopController.setContinueForever(false); // Убедимся, что не бесконечный цикл
                            log.info("  Установлено количество итераций для '" + tg.getName() + "': " + loops);
                        } else {
                            log.warn("  ThreadGroup '" + tg.getName() + "' не использует LoopController. Не удалось установить количество итераций.");
                        }
                    } catch (NumberFormatException e) {
                        log.error("  Неверный формат для параметра 'LOOPS': " + customJMeterProperties.get("LOOPS"), e);
                    }
                }
            }
        }


        // 4. Add Summarizer and ResultCollector
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        Summariser summariser = !summariserName.isEmpty() ? new Summariser(summariserName) : null;

        ResultCollector resultCollector = new ResultCollector(summariser);
        resultCollector.setFilename(RESULTS_FILE_PATH);
        resultCollector.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.reporters.ResultCollector"); // свойство GUI_CLASS, чтобы JMeter знал, как обрабатывать этот элемент при сохранении.
        resultCollector.setName("View Results Tree - from Java"); // Даем имя для отладки
        resultCollector.setEnabled(true);

        // Добавляем ResultCollector к первому элементу корневого узла (обычно это Test Plan)
        // Этот подход гарантирует, что ResultCollector будет собирать данные для всего тестового плана.
        if (testPlanTree.getArray().length > 0) {
            testPlanTree.add(testPlanTree.getArray()[0], resultCollector);
            log.info("ResultCollector добавлен к тестовому плану.");
        } else {
            log.warn("Корневой элемент тестового плана не найден. ResultCollector не будет добавлен.");
        }




        // 5. Сохранение JMX-файла для отладки
        try (FileOutputStream out = new FileOutputStream(new File(GENERATED_JMX_OUTPUT_PATH))) {
            SaveService.saveTree(testPlanTree, out);
            log.info("Тестовый план сохранен в: " + GENERATED_JMX_OUTPUT_PATH);
        } catch (Exception e) {
            log.error("Ошибка при сохранении JMX-файла: " + e.getMessage(), e);
        }

        // 6. Конфигурация и запуск JMeter Engine
        StandardJMeterEngine engine = new StandardJMeterEngine();
        engine.configure(testPlanTree);

        try {
            log.info("Запуск JMeter теста...");
            engine.run();
            log.info("JMeter тест завершен. Результаты в: " + RESULTS_FILE_PATH);
        } catch (Exception e) {
            log.error("Ошибка при выполнении JMeter теста:", e);
        } finally {
            engine.exit();
        }

        // 7. Generate HTML Report
//        try {
//            log.info("Начало генерации HTML-отчета...");
//            ReportGenerator reportGen = new ReportGenerator(RESULTS_FILE_PATH, null); // null для use JMeter property jmeter.reportgenerator.output_dir
//            reportGen.generate();
//            log.info("HTML-отчет успешно сгенерирован в папке: " + DASHBOARD_REPORT_PATH);
//        } catch (GenerationException | ConfigurationException e) {
//            log.error("Ошибка при генерации отчета: " + e.getMessage(), e);
//        }
    }

    public static void main(String[] args) {
        JMeterRunnerParam runner = new JMeterRunnerParam();
        HashMap<String, String> testParameters = new HashMap<>();

        // *** Параметры, которые будут доступны в JMX файле как ${__P(PARAMETER_NAME)} или ${PARAMETER_NAME} ***

        // Параметры для Kafka Consumer/Producer Samplers:
        // Эти ключи должны точно соответствовать именам переменных, используемых в вашем JMX-файле.
        testParameters.put("BOOTSTRAP_SERVERS", "localhost:9092"); // Пример: адрес вашего Kafka брокера
        testParameters.put("TOPIC_NAME", "my_test_topic");         // Пример: имя топика Kafka
        testParameters.put("POLL_TIMEOUT_MS", "500");              // Пример: таймаут опроса для консьюмера
        testParameters.put("SAMPLE_DURATION_MS", "3000");          // Пример: длительность сэмплирования

        // Параметры для Thread Group:
        testParameters.put("THREADS", "10"); // Количество пользователей (потоков)
        testParameters.put("RAMP_UP_PERIOD", "5"); // Время наращивания в секундах
        testParameters.put("LOOPS", "1"); // Количество итераций

        // Уровни логирования для ваших Sampler'ов (если они используются в JMX для отладки)
        testParameters.put("log_level.org.solutions.jmeter_solution3.producers", "INFO"); // DEBUG, INFO, WARN, ERROR
        testParameters.put("log_level.org.solutions.jmeter_solution3.consumers", "INFO");

        // Дополнительные параметры, если нужны:
        // testParameters.put("MESSAGE_SIZE_BYTES", "1024");
        // testParameters.put("PRODUCER_ACK_MODE", "all");

        runner.runJmeter(testParameters);
    }
}