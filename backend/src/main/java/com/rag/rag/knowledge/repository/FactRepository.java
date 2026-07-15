package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.fact.Fact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FactRepository extends JpaRepository<Fact, UUID> {

    void deleteByFilePath(String filePath);

    @EntityGraph(attributePaths = {"mention", "mention.entity"})
    @Query("SELECT f FROM Fact f")
    List<Fact> findAllWithMentionAndEntity();
}
