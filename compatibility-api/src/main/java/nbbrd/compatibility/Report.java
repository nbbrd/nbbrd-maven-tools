package nbbrd.compatibility;

import java.util.List;

@lombok.Value
@lombok.Builder
public class Report {

    public static final Report EMPTY = Report.builder().build();

    @lombok.Singular
    List<ReportItem> items;
}
