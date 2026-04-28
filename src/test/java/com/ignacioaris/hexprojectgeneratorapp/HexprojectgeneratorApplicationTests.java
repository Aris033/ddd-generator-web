package com.ignacioaris.hexprojectgeneratorapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class HexprojectgeneratorAppApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	/**
	 * Verifica que el contexto de Spring Boot arranca correctamente.
	 */
	@Test
	void contextLoads() {
	}

	@Test
	void healthReturnsOk() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void defaultsReturnsPresetValues() throws Exception {
		mockMvc.perform(get("/api/defaults"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.project").value("DemoHexProject"))
				.andExpect(jsonPath("$.groupId").value("com.ignacio.demo"))
				.andExpect(jsonPath("$.persistence").value("h2"))
				.andExpect(jsonPath("$.examples[0].name").value("User"))
				.andExpect(jsonPath("$.examples[1].name").value("Order"));
	}

	@Test
	void generateReturnsZipWithExpectedPaths() throws Exception {
		byte[] body = mockMvc.perform(post("/api/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/zip"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"DemoHexProject.zip\""))
				.andReturn()
				.getResponse()
				.getContentAsByteArray();

		assertThat(zipEntries(body)).anyMatch(entry -> entry.endsWith("modules/user/domain/User.java"));
	}

	@Test
	void generateRejectsInvalidRequest() throws Exception {
		mockMvc.perform(post("/api/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "project": "../bad",
								  "groupId": "bad",
								  "persistence": "unknown",
								  "examples": []
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.errors").isArray());
	}

	private static String validRequest() {
		return """
				{
				  "project": "DemoHexProject",
				  "groupId": "com.ignacio.demo",
				  "artifactId": "",
				  "persistence": "h2",
				  "examples": [
				    {
				      "name": "User",
				      "structure": "name:String,email:String,age:Integer,birthDate:LocalDate"
				    },
				    {
				      "name": "Order",
				      "structure": "code:String,total:BigDecimal,paid:Boolean,createdOn:LocalDate"
				    }
				  ]
				}
				""";
	}

	private static java.util.List<String> zipEntries(byte[] zipBytes) throws Exception {
		java.util.List<String> entries = new java.util.ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			java.util.zip.ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.add(entry.getName());
			}
		}
		return entries;
	}
}
