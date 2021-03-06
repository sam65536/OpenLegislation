package gov.nysenate.openleg.dao.spotcheck;

import com.google.common.collect.Sets;
import gov.nysenate.openleg.dao.base.*;
import gov.nysenate.openleg.model.base.SessionYear;
import gov.nysenate.openleg.model.spotcheck.*;
import gov.nysenate.openleg.service.spotcheck.base.MismatchNotFoundEx;
import gov.nysenate.openleg.service.spotcheck.base.MismatchUtils;
import gov.nysenate.openleg.util.SpotCheckReportUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static gov.nysenate.openleg.dao.spotcheck.SqlSpotCheckReportQuery.*;
import static gov.nysenate.openleg.util.DateUtils.toDate;

/**
 * The AbstractSpotCheckReportDao implements all the functionality required by SpotCheckReportDao
 * regardless of the content key specified. This class must be subclasses with a concrete type for
 * the ContentKey. The subclass will need to handle just the conversions for the ContentKey class.
 *
 * @param <ContentKey>
 */
public abstract class AbstractSpotCheckReportDao<ContentKey> extends SqlBaseDao
        implements SpotCheckReportDao<ContentKey> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpotCheckReportDao.class);

    /** --- Abstract Methods --- */

    /**
     * Subclasses should implement this conversion from a Map containing certain key/val pairs to
     * an instance of ContentKey. This is needed since the keys are stored as an hstore in the
     * database.
     *
     * @param keyMap Map<String, String>
     * @return ContentKey
     */
    public abstract ContentKey getKeyFromMap(Map<String, String> keyMap);

    /**
     * Subclasses should implement a conversion from an instance of ContentKey to a Map of
     * key/val pairs that fully represent that ContentKey.
     *
     * @param key ContentKey
     * @return Map<String, String>
     */
    public abstract Map<String, String> getMapFromKey(ContentKey key);

    /** --- Implemented Methods --- */

    /**
     * {@inheritDoc}
     */
    @Override
    public DeNormSpotCheckMismatch getMismatch(int mismatchId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId);
        String sql = SqlSpotCheckReportQuery.GET_MISMATCH.getSql(schema());
        List<DeNormSpotCheckMismatch> results = jdbcNamed.query(sql, params, new MismatchMapper());
        if (results.size() == 0) {
            throw new MismatchNotFoundEx(mismatchId);
        }
        return results.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PaginatedList<DeNormSpotCheckMismatch> getMismatches(MismatchQuery query, LimitOffset limitOffset) {
        MapSqlParameterSource params = activeMismatchParams(SpotCheckReportUtils.getReportEndDateTime(query.getReportDate()), query.getDataSource())
                .addValue("contentTypes", query.getContentTypes().stream().map(Enum::name).collect(Collectors.toSet()))
                .addValue("state", query.getState().name())
                .addValue("observedStartDateTime", query.getObservedStartDateTime())
                .addValue("firstSeenStartDateTime", query.getFirstSeenStartDateTime())
                .addValue("firstSeenEndDateTime", query.getFirstSeenEndDateTime())
                .addValue("observedEndDateTime", query.getObservedEndDateTime())
                .addValue("ignoreStatuses", query.getIgnoredStatuses().stream().map(Enum::name).collect(Collectors.toSet()))
                .addValue("mismatchTypes", extractEnumSetParams(query.getMismatchTypes()));
        String sql = SqlSpotCheckReportQuery.GET_MISMATCHES.getSql(schema(), query.getOrderBy(), limitOffset);
        PaginatedRowHandler<DeNormSpotCheckMismatch> handler = new PaginatedRowHandler<>(limitOffset, "total_rows", new MismatchMapper());
        jdbcNamed.query(sql, params, handler);
        return handler.getList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MismatchStatusSummary getMismatchStatusSummary(LocalDate reportDate, SpotCheckDataSource datasource,
                                                          SpotCheckContentType contentType, Set<SpotCheckMismatchIgnore> ignoreStatuses) {
        MapSqlParameterSource params = activeMismatchParams(SpotCheckReportUtils.getReportEndDateTime(reportDate), datasource)
                .addValue("contentType", contentType.name())
                .addValue("ignoreStatuses", extractEnumSetParams(ignoreStatuses))
                .addValue("reportStartDateTime", SpotCheckReportUtils.getReportStartDateTime(reportDate))
                .addValue("reportEndDateTime", SpotCheckReportUtils.getReportEndDateTime(reportDate));
        String sql = SqlSpotCheckReportQuery.MISMATCH_STATUS_SUMMARY.getSql(schema());
        MismatchStatusSummaryHandler summaryHandler = new MismatchStatusSummaryHandler();
        jdbcNamed.query(sql, params, summaryHandler);
        return summaryHandler.getSummary();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MismatchTypeSummary getMismatchTypeSummary(LocalDate reportDate, SpotCheckDataSource datasource,
                                                      SpotCheckContentType contentType, MismatchStatus mismatchStatus,
                                                      Set<SpotCheckMismatchIgnore> ignoreStatuses) {
        MapSqlParameterSource params = activeMismatchParams(SpotCheckReportUtils.getReportEndDateTime(reportDate), datasource)
                .addValue("ignoreStatuses", extractEnumSetParams(ignoreStatuses))
                .addValue("observedStartDateTime", mismatchStatus.getObservedStartDateTime(reportDate))
                .addValue("firstSeenStartDateTime", mismatchStatus.getFirstSeenStartDateTime(reportDate))
                .addValue("firstSeenEndDateTime", mismatchStatus.getFirstSeenEndDateTime(reportDate))
                .addValue("observedEndDateTime", mismatchStatus.getObservedEndDateTime(reportDate))
                .addValue("contentType", contentType.name())
                .addValue("state", mismatchStatus.getState().name());
        String sql = SqlSpotCheckReportQuery.MISMATCH_TYPE_SUMMARY.getSql(schema());
        MismatchTypeSummaryHandler summaryHandler = new MismatchTypeSummaryHandler(contentType);
        jdbcNamed.query(sql, params, summaryHandler);
        return summaryHandler.getSummary();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MismatchContentTypeSummary getMismatchContentTypeSummary(LocalDate reportDate, SpotCheckDataSource datasource,
                                                                    Set<SpotCheckMismatchIgnore> ignoreStatuses) {
          MapSqlParameterSource params = activeMismatchParams(SpotCheckReportUtils.getReportEndDateTime(reportDate), datasource)
                  .addValue("ignoreStatuses", extractEnumSetParams(ignoreStatuses))
                  .addValue("reportStartDateTime", SpotCheckReportUtils.getReportStartDateTime(reportDate))
                  .addValue("reportEndDateTime", SpotCheckReportUtils.getReportEndDateTime(reportDate));
        String sql = SqlSpotCheckReportQuery.MISMATCH_CONTENT_TYPE_SUMMARY.getSql(schema());
        MismatchContentTypeSummaryHandler summaryHandler = new MismatchContentTypeSummaryHandler();
        jdbcNamed.query(sql, params, summaryHandler);
        return summaryHandler.getSummary();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveReport(SpotCheckReport<ContentKey> report) {
        int reportId = insertReport(report);
        report.setId(reportId);
        // Return early if the observations have not been set
        if (report.getObservations() == null) {
            logger.warn("The observations have not been set on this report.");
            return;
        }

        List<DeNormSpotCheckMismatch> reportMismatches = reportToDeNormMismatches(report);
        List<DeNormSpotCheckMismatch> currentMismatches = getCurrentMismatches(report);

        reportMismatches.addAll(closedMismatches(report, reportMismatches, currentMismatches));
        reportMismatches = MismatchUtils.copyIgnoreStatuses(currentMismatches, reportMismatches);
        reportMismatches = MismatchUtils.updateIgnoreStatus(reportMismatches);
        reportMismatches = MismatchUtils.updateFirstSeenDateTime(reportMismatches, currentMismatches);

        insertMismatches(reportMismatches);
    }

    private List<DeNormSpotCheckMismatch> closedMismatches(SpotCheckReport<ContentKey> report,
                                                           List<DeNormSpotCheckMismatch> reportMismatches,
                                                           List<DeNormSpotCheckMismatch> currentMismatches) {
        return MismatchUtils.deriveClosedMismatches(reportMismatches, currentMismatches, report);
    }

    private List<DeNormSpotCheckMismatch> getCurrentMismatches(SpotCheckReport<ContentKey> report) {
        MismatchQuery query = new MismatchQuery(report.getReportDateTime().toLocalDate(),
                                                report.getReferenceType().getDataSource(),
                                                MismatchStatus.OPEN,
                                                Sets.newHashSet(report.getReferenceType().getContentType()))
                .withIgnoredStatuses(EnumSet.allOf(SpotCheckMismatchIgnore.class));
        return getMismatches(query, LimitOffset.ALL).getResults();
    }

    private int insertReport(SpotCheckReport<ContentKey> report) {
        ImmutableParams reportParams = ImmutableParams.from(getReportIdParams(report));
        KeyHolder reportIdHolder = new GeneratedKeyHolder();
        jdbcNamed.update(INSERT_REPORT.getSql(schema()), reportParams, reportIdHolder, new String[]{"id"});
        return reportIdHolder.getKey().intValue();
    }

    private void insertMismatches(List<DeNormSpotCheckMismatch> mismatches) {
        List<MapSqlParameterSource> params = mismatches.stream()
                .map(this::mismatchParams)
                .collect(Collectors.toList());
        String sql = INSERT_MISMATCH.getSql(schema());
        jdbcNamed.batchUpdate(sql, params.stream().toArray(MapSqlParameterSource[]::new));
    }

    /**
     * Parameters used in the {@link SqlSpotCheckReportQuery} ACTIVE_MISMATCHES query.
     */
    private MapSqlParameterSource activeMismatchParams(LocalDateTime toDateTime, SpotCheckDataSource dataSource) {
        return new MapSqlParameterSource()
                .addValue("sessionStartDateTime", SessionYear.of(toDateTime.getYear()).getStartDateTime())
                .addValue("reportEndDateTime", toDateTime)
                .addValue("datasource", dataSource.name());
    }

    private MapSqlParameterSource mismatchParams(DeNormSpotCheckMismatch mismatch) {
        return new MapSqlParameterSource()
                .addValue("key", toHstoreString(getMapFromKey((ContentKey) mismatch.getKey())))
                .addValue("mismatchType", mismatch.getType().name())
                .addValue("reportId", mismatch.getReportId())
                .addValue("datasource", mismatch.getDataSource().name())
                .addValue("contentType", mismatch.getContentType().name())
                .addValue("referenceType", mismatch.getReferenceId().getReferenceType().name())
                .addValue("mismatchStatus", mismatch.getState().name())
                .addValue("referenceData", mismatch.getReferenceData())
                .addValue("observedData", mismatch.getObservedData())
                .addValue("notes", mismatch.getNotes())
                .addValue("issueIds", toPostgresArray(mismatch.getIssueIds()))
                .addValue("ignoreLevel", mismatch.getIgnoreStatus().name())
                .addValue("reportDateTime", mismatch.getReportDateTime())
                .addValue("observedDateTime", mismatch.getObservedDateTime())
                .addValue("firstSeenDateTime", mismatch.getFirstSeenDateTime())
                .addValue("referenceActiveDateTime", mismatch.getReferenceId().getRefActiveDateTime());
    }

    private String toPostgresArray(Set<String> strings) {
        return "{" + StringUtils.join(strings, ',') + "}";
    }

    /**
     * Converts SpotCheckMismatches in a SpotCheckReport into DeNormSpotCheckMismaches.
     * Initializes firstSeenDateTime to the observedDateTime.
     */
    private List<DeNormSpotCheckMismatch> reportToDeNormMismatches(SpotCheckReport<ContentKey> report) {
        List<DeNormSpotCheckMismatch> mismatches = new ArrayList<>();
        for (SpotCheckObservation<ContentKey> ob : report.getObservations().values()) {
            // Skip if no mismatches in the observation
            if (ob.getMismatches().size() == 0) {
                continue;
            }
            for (SpotCheckMismatch m : ob.getMismatches().values()) {
                DeNormSpotCheckMismatch mismatch = new DeNormSpotCheckMismatch<>(ob.getKey(), m.getMismatchType(),
                        report.getReferenceType().getDataSource());
                mismatch.setReportId(report.getId());
                mismatch.setContentType(report.getReferenceType().getContentType());
                mismatch.setReferenceId(ob.getReferenceId());
                mismatch.setReferenceData(m.getReferenceData());
                mismatch.setObservedData(m.getObservedData());
                mismatch.setNotes(m.getNotes());
                mismatch.setObservedDateTime(ob.getObservedDateTime());
                mismatch.setReportDateTime(report.getReportDateTime());
                if (m.getIgnoreStatus() != null)
                    mismatch.setIgnoreStatus(m.getIgnoreStatus());
                mismatch.setIssueIds(new HashSet<>(m.getIssueIds()));
                mismatches.add(mismatch);
            }
        }
        return mismatches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMismatchIgnoreStatus(int mismatchId, SpotCheckMismatchIgnore ignoreStatus) {
        if (ignoreStatus == null) {
            throw new IllegalArgumentException("Cannot set mismatch ignore state to null.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId)
                .addValue("ignoreStatus", ignoreStatus.name());
        String sql = SqlSpotCheckReportQuery.UPDATE_MISMATCH_IGNORE.getSql(schema());
        jdbcNamed.update(sql, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateIssueId(int mismatchId, String issueId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId)
                .addValue("issueId", issueId);
        String sql = SqlSpotCheckReportQuery.UPDATE_ISSUE_ID.getSql(schema());
        jdbcNamed.update(sql, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIssueId(int mismatchId, String issueId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId)
                .addValue("issueId", issueId);
        String sql = SqlSpotCheckReportQuery.ADD_ISSUE_ID.getSql(schema());
        jdbcNamed.update(sql, params);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteIssueId(int mismatchId, String issueId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId)
                .addValue("issueId", issueId);
        String sql = SqlSpotCheckReportQuery.DELETE_ISSUE_ID.getSql(schema());
        jdbcNamed.update(sql, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllIssueId(int mismatchId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mismatchId", mismatchId);
        String sql = SqlSpotCheckReportQuery.DELETE_ALL_ISSUE_ID.getSql(schema());
        jdbcNamed.update(sql, params);
    }

    /** Convert a Set containing enums into a Set containing each enum's name. */
    private <E extends Enum<E>> Set<String> extractEnumSetParams(Set<E> enumSet) {
        return enumSet.stream().map(Enum::name).collect(Collectors.toSet());
    }

    /**
     * --- Helper Classes ---
     */

    private class MismatchMapper implements RowMapper<DeNormSpotCheckMismatch> {

        @Override
        public DeNormSpotCheckMismatch<ContentKey> mapRow(ResultSet rs, int rowNum) throws SQLException {
            ContentKey key = getKeyFromMap(hstoreStringToMap(rs.getString("key")));
            SpotCheckMismatchType type = SpotCheckMismatchType.valueOf(rs.getString("type"));
            SpotCheckDataSource dataSource = SpotCheckDataSource.valueOf(rs.getString("datasource"));
            DeNormSpotCheckMismatch mismatch = new DeNormSpotCheckMismatch<>(key, type, dataSource);
            mismatch.setMismatchId(rs.getInt("mismatch_id"));
            mismatch.setReportId(rs.getInt("report_id"));
            mismatch.setState(MismatchState.valueOf(rs.getString("state")));
            mismatch.setContentType(SpotCheckContentType.valueOf(rs.getString("content_type")));
            mismatch.setReferenceData(rs.getString("reference_data"));
            mismatch.setObservedData(rs.getString("observed_data"));
            mismatch.setReportDateTime(getLocalDateTimeFromRs(rs, "report_date_time"));
            mismatch.setObservedDateTime(getLocalDateTimeFromRs(rs, "observed_date_time"));
            mismatch.setFirstSeenDateTime(getLocalDateTimeFromRs(rs, "first_seen_date_time"));
            mismatch.setNotes(rs.getString("notes"));
            mismatch.setIgnoreStatus(SpotCheckMismatchIgnore.valueOf(rs.getString("ignore_status")));
            String[] issue_idss = getArrayFromPgRs(rs, "issue_ids");
            mismatch.setIssueIds(Sets.newHashSet(issue_idss));

            SpotCheckRefType refType = SpotCheckRefType.valueOf(rs.getString("reference_type"));
            LocalDateTime refActiveDateTime = getLocalDateTimeFromRs(rs, "reference_active_date_time");
            mismatch.setReferenceId(new SpotCheckReferenceId(refType, refActiveDateTime));
            return mismatch;
        }
    }

    private class MismatchStatusSummaryHandler implements RowCallbackHandler {

        private MismatchStatusSummary summary = new MismatchStatusSummary();

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            MismatchStatus status = MismatchStatus.valueOf(rs.getString("status"));
            int count = rs.getInt("count");
            summary.putSummary(status, count);
        }

        protected MismatchStatusSummary getSummary() {
            summary.putSummary(MismatchStatus.OPEN, summary.getSummary().get(MismatchStatus.NEW)+summary.getSummary().get(MismatchStatus.EXISTING));
            return summary;
        }
    }

    private class MismatchTypeSummaryHandler implements RowCallbackHandler {

        private MismatchTypeSummary summary;

        public MismatchTypeSummaryHandler(SpotCheckContentType contentType) {
            summary = new MismatchTypeSummary(contentType);
        }

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            SpotCheckMismatchType spotCheckMismatchType = SpotCheckMismatchType.valueOf(rs.getString("type"));
            int count = rs.getInt("count");
            summary.addSpotCheckMismatchTypeCount(spotCheckMismatchType, count);
        }

        protected MismatchTypeSummary getSummary() {
            int all = 0;
            for (Map.Entry<SpotCheckMismatchType, Integer> entry:summary.getSummary().entrySet()){
                all += entry.getValue();
            }
            summary.getSummary().put(SpotCheckMismatchType.All,all);
            return summary;
        }
    }

    private class MismatchContentTypeSummaryHandler implements RowCallbackHandler {

        private MismatchContentTypeSummary summary = new MismatchContentTypeSummary();

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            SpotCheckContentType contentType = SpotCheckContentType.valueOf(rs.getString("content_type"));
            int count = rs.getInt("count");
            summary.addSpotCheckMismatchContentTypeCount(contentType, count);
        }

        protected MismatchContentTypeSummary getSummary() {
            return summary;
        }
    }

    private MapSqlParameterSource getReportIdParams(SpotCheckReport<ContentKey> report) {
        return new MapSqlParameterSource()
                .addValue("referenceType", report.getReferenceType().name())
                .addValue("reportDateTime", toDate(report.getReportDateTime()))
                .addValue("referenceDateTime", toDate(report.getReferenceDateTime()))
                .addValue("notes", report.getNotes());
    }

}
