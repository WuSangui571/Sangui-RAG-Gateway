package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface DocumentChunkEmbeddingMapper extends BaseMapper<DocumentChunkEmbeddingEntity> {

    @Insert("""
            INSERT INTO rag_document_chunk_embedding (
                user_id,
                knowledge_base_id,
                document_id,
                chunk_id,
                embedding_model,
                embedding_dimension,
                embedding,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{knowledgeBaseId},
                #{documentId},
                #{chunkId},
                #{embeddingModel},
                #{embeddingDimension},
                #{embedding}::vector,
                #{createdAt},
                #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEmbedding(DocumentChunkEmbeddingEntity entity);
}
