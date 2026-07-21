package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Visual path: selects evidence ids via {@link ClaimAnswerComposer}, renders only claim text. */
@Service
@RequiredArgsConstructor
public class VerifiedVisualAnswerService {

    public static final String MATCH_FALLBACK_ANSWER = ClaimAnswerComposer.EMPTY_FALLBACK;
    public static final String NO_VISUAL_EVIDENCE = "Nie znaleziono potwierdzonych dowodów wizualnych.";

    private final ClaimAnswerComposer claimAnswerComposer;

    public String answer(String question, List<VisualQueryMatch> matches) {
        return answer(question, matches, List.of());
    }

    public String answer(String question, List<VisualQueryMatch> matches, List<String> certainParticipantNames) {
        if (matches == null || matches.isEmpty()) {
            return NO_VISUAL_EVIDENCE;
        }
        List<GroundedVisualClaim> claims = matches.stream()
                .flatMap(match -> match.claims().stream())
                .distinct()
                .toList();
        if (claims.isEmpty()) {
            List<String> names = certainParticipantNames == null ? List.of() : certainParticipantNames.stream()
                    .filter(name -> name != null && !name.isBlank()).distinct().toList();
            return names.isEmpty() ? MATCH_FALLBACK_ANSWER : ChatAnswerGrounding.formatParticipantRoster(names);
        }
        return answerFromClaims(question, claims);
    }

    public String answerFromClaims(String question, List<GroundedVisualClaim> claims) {
        ClaimAnswerComposer.ClaimAnswerResult result = claimAnswerComposer.answerFromClaims(question, claims);
        return result.hasGroundedProse() ? result.answer() : MATCH_FALLBACK_ANSWER;
    }
}
