package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    @Insert("""
            INSERT INTO rag_document_chunk (
                user_id,
                knowledge_base_id,
                document_id,
                chunk_index,
                content,
                token_count,
                metadata,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{knowledgeBaseId},
                #{documentId},
                #{chunkIndex},
                #{content},
                #{tokenCount},
                CAST(#{metadata} AS JSONB),
                #{createdAt},
                #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChunk(DocumentChunkEntity entity);
}
