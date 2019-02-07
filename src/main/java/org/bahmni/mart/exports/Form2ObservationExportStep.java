package org.bahmni.mart.exports;

import org.bahmni.mart.BatchUtils;
import org.bahmni.mart.config.job.JobDefinitionUtil;
import org.bahmni.mart.config.job.model.JobDefinition;
import org.bahmni.mart.exports.writer.DatabaseObsWriter;
import org.bahmni.mart.form.domain.BahmniForm;
import org.bahmni.mart.form.domain.Concept;
import org.bahmni.mart.form.domain.Obs;
import org.bahmni.mart.form2.Form2ObservationProcessor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bahmni.mart.BatchUtils.stepNumber;

@Component
@Scope(value = "prototype")
public class Form2ObservationExportStep implements ObservationExportStep {

    private BahmniForm form;
    private JobDefinition jobDefinition;
    private String obsReaderSql;

    @Value("classpath:sql/form2Obs.sql")
    private Resource obsReaderSqlResource;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectFactory<Form2ObservationProcessor> observationProcessorFactory;

    @Autowired
    private ObjectFactory<DatabaseObsWriter> databaseObsWriterObjectFactory;

    @Override
    public Step getStep() {
        return stepBuilderFactory.get(getStepName("Insertion Step"))
                .<Map<String, Object>, List<Obs>>chunk(100)
                .reader(obsReader(false))
                .processor(observationProcessor())
                .writer(getWriter())
                .build();
    }

    @Override
    public void setJobDefinition(JobDefinition jobDefinition) {
        this.jobDefinition = jobDefinition;
    }

    @Override
    public void setForm(BahmniForm form) {
        this.form = form;
    }

    private JdbcCursorItemReader<Map<String, Object>> obsReader(boolean voided) {
        String sql = getProcessedSql(voided);
        JdbcCursorItemReader<Map<String, Object>> reader = new JdbcCursorItemReader<>();
        reader.setDataSource(dataSource);
        reader.setSql(sql);
        reader.setRowMapper(new ColumnMapRowMapper());
        return reader;
    }

    private Form2ObservationProcessor observationProcessor() {
        Form2ObservationProcessor observationProcessor = observationProcessorFactory.getObject();
        observationProcessor.setForm(form);
        return observationProcessor;
    }

    private DatabaseObsWriter getWriter() {
        DatabaseObsWriter writer = databaseObsWriterObjectFactory.getObject();
        writer.setJobDefinition(jobDefinition);
        writer.setForm(form);
        writer.setAddMoreMultiSelectEnabled(JobDefinitionUtil.isAddMoreMultiSelectEnabled(jobDefinition));
        return writer;
    }

    private String getProcessedSql(boolean voided) {
        String obsReaderSql = this.obsReaderSql;
        List<String> conceptNames = form.getFields()
                .stream()
                .map(field -> field.getName())
                .collect(Collectors.toList());
        obsReaderSql = BatchUtils.constructSqlWithParameter(obsReaderSql, "voided", voided);
        obsReaderSql = BatchUtils.constructSqlWithParameter(obsReaderSql, "formName",
                getFormName());
        obsReaderSql = BatchUtils.constructSqlWithParameter(obsReaderSql, "conceptNames", conceptNames);
        obsReaderSql = BatchUtils.constructSqlWithParameter(obsReaderSql, "locale", jobDefinition.getLocale());
        obsReaderSql = BatchUtils.constructSqlWithParameter(obsReaderSql,
                "conceptReferenceSource", jobDefinition.getConceptReferenceSource());
        return obsReaderSql;
    }

    private String getFormName() {
        if (form.getDepthToParent() == 0) {
            Concept formNameConcept = form.getFormName();
            return formNameConcept.getName();
        } else {
            BahmniForm rootForm = form.getRootForm();
            Concept formNameConcept = rootForm.getFormName();
            return formNameConcept.getName();
        }
    }

    @Override
    public Step getRemovalStep() {
        return null;
    }

    @PostConstruct
    public void postConstruct() {
        this.obsReaderSql = BatchUtils.convertResourceOutputToString(obsReaderSqlResource);
    }

    public String getStepName(String prefix) {
        stepNumber++;
        String formName = String.format("%s-%d %s", prefix, stepNumber, form.getFormName().getName());
        return formName.substring(0, Math.min(formName.length(), 100));
    }
}