package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    @Select("<script>" +
            "SELECT * FROM rag_document_chunk " +
            "WHERE user_id = #{userId} " +
            "AND knowledge_base_id = #{knowledgeBaseId} " +
            "<choose>" +
            "<when test='ids != null and ids.size() > 0'>" +
            "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</when>" +
            "<otherwise>AND 1 = 0</otherwise>" +
            "</choose>" +
            "</script>")
    List<DocumentChunkEntity> selectByIdsAndUserAndKb(@Param("userId") Long userId,
                                                       @Param("knowledgeBaseId") Long knowledgeBaseId,
                                                       @Param("ids") List<Long> ids);
}
