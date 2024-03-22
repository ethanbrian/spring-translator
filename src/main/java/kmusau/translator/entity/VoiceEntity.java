package kmusau.translator.entity;

import java.io.File;
import java.net.URL;
import java.util.Date;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import kmusau.translator.enums.StatusTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "voice")
public class VoiceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long voiceId;
	
	private Date dateCreated;
	
	private Date dateModified;
	
//	private File filepath;

	private String fileUrl;

	@Column(length = 1200)
	private String presignedUrl;
	
	@Enumerated(EnumType.ORDINAL)
	private StatusTypes status;

	@Column(name = "translated_sentence_id")
	private Long translatedSentenceId;

	@JoinColumn(name = "translated_sentence_id", referencedColumnName = "translatedSentenceId", updatable = false, insertable = false)
	@ManyToOne(fetch = FetchType.LAZY)
	private TranslatedSentenceEntity translatedSentence;


}
