package ru.nosql.lab3.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Document("booking_requests")
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BookingRequest {

    @Id
    private String id;

    private Integer userId;

    private String roomId;

    private String ruleId;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private String purpose;

    private int attendeesCount;

    private RequestStatus status;

    private Instant createdAt;

    private Instant reviewedAt;

    private String reviewerComment;

    private Instant updatedAt;

    private List<RequestHistoryEntry> history = new ArrayList<>();

    private List<String> tags = new ArrayList<>();

    public void approve(Instant reviewedAt) {
        status = RequestStatus.APPROVED;
        this.reviewedAt = reviewedAt;
        reviewerComment = null;
        recordAction(RequestAction.APPROVED, reviewedAt, null);
    }

    public void reject(Instant reviewedAt, String reviewerComment) {
        status = RequestStatus.REJECTED;
        this.reviewedAt = reviewedAt;
        this.reviewerComment = reviewerComment;
        recordAction(RequestAction.REJECTED, reviewedAt, reviewerComment);
    }

    public void recordRuleRevision(Instant revisedAt, String comment) {
        recordAction(RequestAction.RULE_REVISED, revisedAt, comment);
    }

    public void assignRule(String ruleId, String name, Instant now) {
        this.ruleId = ruleId;
        recordAction(RequestAction.RULE_ASSIGNED, now, "Назначено правило: " + name);
    }

    private void recordAction(RequestAction action, Instant occurredAt, String comment) {
        updatedAt = occurredAt;
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(new RequestHistoryEntry(action, occurredAt, comment));
    }
}
