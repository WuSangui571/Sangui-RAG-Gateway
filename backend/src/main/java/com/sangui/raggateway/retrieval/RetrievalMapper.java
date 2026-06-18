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
                   c.knowledge_base_id AS knowledge_base_id,
                   c.chunk_index      AS chunk_index,
                   d.original_filename AS source_filename,
                   c.content          AS content,
                   c.metadata::text   AS metadata,
                   1 - (e.embedding <=> #{queryVector}::vector) AS similarity
            FROM rag_document_chunk_embedding e
                     JOIN rag_document_chunk c ON c.id = e.chunk_id
                     LEFT JOIN rag_document d
                       ON d.id = c.document_id
                      AND d.user_id = e.user_id
                      AND d.knowledge_base_id = e.knowledge_base_id
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
