package gov.nysenate.openleg.processor.calendar;

import gov.nysenate.openleg.annotation.SillyTest;
import gov.nysenate.openleg.model.spotcheck.calendar.FloorCalendarSpotcheckReference;
import gov.nysenate.openleg.processor.spotcheck.calendar.CalendarHTMLParser;
import gov.nysenate.openleg.util.OutputUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Created by kyle on 10/8/14.
 */
@Category(SillyTest.class)
public class CalendarHTMLParserTest {

    private static final Logger logger = LoggerFactory.getLogger(CalendarHTMLParserTest.class);



    File input = new File("/home/kyle/Test");

    @Test
    public void parseCalendars() throws Exception{
        CalendarHTMLParser.getSpotcheckReference(input);
        FloorCalendarSpotcheckReference ref = CalendarHTMLParser.getSpotcheckReference(input);
        logger.info(OutputUtils.toJson(ref));

    }

}
