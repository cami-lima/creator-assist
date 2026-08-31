package br.com.creatorassist.memory

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CreatorMonthStateRepository : JpaRepository<CreatorMonthState, Long> {
    fun findTopByCreatorIdOrderByReferenceMonthDesc(creatorId: String): CreatorMonthState?
}
