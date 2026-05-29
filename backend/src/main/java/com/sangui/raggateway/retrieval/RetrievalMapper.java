package com.sangui.raggateway.retrieval;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RetrievalMapper {

    @Select("""
            SELECT c.id               AS chunk_id,
                   c.document_id      AS document_id,
                   c.content          AS content,
                   c.metadata::text   AS metadata,
                   1 - (e.embedding <=> #{queryVector}::vector) AS similarity
            FROM rag_document_chunk_embedding e
                     JOIN rag_document_chunk c ON c.id = e.chunk_id
            WHERE e.user_id = #{userId}
              AND e.knowledge_base_id = #{knowledgeBaseId}
            ORDER BY e.embedding <=> #{queryVector}::vector
            LIMIT #{limit}
            """)
    List<ChunkRow> retrieveChunks(@Param("queryVector") String queryVector,
                                  @Param("userId") Long userId,
                                  @Param("knowledgeBaseId") Long knowledgeBaseId,
                                  @Param("limit") int limit);
}
