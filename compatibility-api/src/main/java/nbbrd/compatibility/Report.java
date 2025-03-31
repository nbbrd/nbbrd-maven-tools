package nbbrd.compatibility;

import java.util.List;

@lombok.Value
@lombok.Builder
public class Report {

    @lombok.Singular
    List<ReportItem> items;
}
