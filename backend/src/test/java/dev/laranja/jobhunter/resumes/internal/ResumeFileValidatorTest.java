package dev.laranja.jobhunter.resumes.internal;

import java.util.Arrays;

import dev.laranja.jobhunter.TestFiles;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeFileValidatorTest {

	private final ResumeFileValidator validator = new ResumeFileValidator();

	@Test
	void acceptsPdfAndDocxByContent() {
		assertThat(this.validator.validate(TestFiles.pdf("a"))).isEqualTo(ResumeFileType.PDF);
		assertThat(this.validator.validate(TestFiles.docx("a"))).isEqualTo(ResumeFileType.DOCX);
	}

	@Test
	void rejectsAnExecutableWhateverItsName() {
		assertThatThrownBy(() -> this.validator.validate(TestFiles.executable()))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("PDF ou DOCX");
	}

	@Test
	void rejectsAZipThatIsNotAWordDocument() {
		byte[] zip = { 'P', 'K', 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		assertThatThrownBy(() -> this.validator.validate(zip)).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void rejectsEmptyAndOversizedFiles() {
		byte[] oversized = Arrays.copyOf(TestFiles.pdf("big"), (int) ResumeFileValidator.MAX_SIZE_BYTES + 1);

		assertThatThrownBy(() -> this.validator.validate(new byte[0])).hasMessageContaining("vazio");
		assertThatThrownBy(() -> this.validator.validate(oversized)).hasMessageContaining("5 MB");
	}

}
