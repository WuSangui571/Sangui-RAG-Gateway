package com.sangui.raggateway.retrieval;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalMapperTest {

    @Test
    void shouldKeepRetrievalSqlScopedToReadyDocumentAndEmbeddingChunkBoundary() throws Exception {
        Method method = RetrievalMapper.class.getMethod(
                "retrieveChunks", String.class, Long.class, Long.class, int.class);
        Select select = method.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        String sql = normalizeSql(String.join(" ", select.value()));

        assertThat(sql)
                .contains("FROM rag_document_chunk_embedding e")
                .contains("JOIN rag_document_chunk c ON c.id = e.chunk_id")
                .contains("AND c.user_id = e.user_id")
                .contains("AND c.knowledge_base_id = e.knowledge_base_id")
                .contains("AND c.document_id = e.document_id")
                .contains("JOIN rag_document d ON d.id = e.document_id")
                .contains("AND d.user_id = e.user_id")
                .contains("AND d.knowledge_base_id = e.knowledge_base_id")
                .contains("AND d.status = 'READY'")
                .contains("WHERE e.user_id = #{userId}")
                .contains("AND e.knowledge_base_id = #{knowledgeBaseId}")
                .contains("ORDER BY e.embedding <=> #{queryVector}::vector");
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
