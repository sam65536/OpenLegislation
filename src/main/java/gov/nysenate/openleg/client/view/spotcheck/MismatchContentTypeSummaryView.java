package gov.nysenate.openleg.client.view.spotcheck;

import gov.nysenate.openleg.client.view.base.MapView;
import gov.nysenate.openleg.client.view.base.ViewObject;
import gov.nysenate.openleg.model.spotcheck.MismatchContentTypeSummary;
import gov.nysenate.openleg.model.spotcheck.SpotCheckContentType;

public class MismatchContentTypeSummaryView implements ViewObject{

    protected MapView<SpotCheckContentType, Integer> summary;

    public MismatchContentTypeSummaryView(MismatchContentTypeSummary summary){
        this.summary = MapView.ofIntMap(summary.getSummary());
    }

    public MapView<SpotCheckContentType, Integer> getSummary(){
        return summary;
    }
    @Override
    public String getViewType() {
        return "mismatch-content-type-summary";
    }

}
