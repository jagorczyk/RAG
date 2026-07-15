package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.entity.EntityAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityAliasRepository extends JpaRepository<EntityAlias, UUID> {

    Optional<EntityAlias> findFirstByAliasIgnoreCase(String alias);

    Optional<EntityAlias> findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase(String alias, String type);
}
