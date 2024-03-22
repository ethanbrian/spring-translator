package kmusau.translator.DTOs.sentenceDTOs;

import kmusau.translator.entity.TranslatedSentenceEntity;
import kmusau.translator.entity.VoiceEntity;
import lombok.*;

@Data
@AllArgsConstructor
public class CompletedSentenceItemDto {
    private Long sentenceId;
    private String sentenceText;
    private Long translatedSentenceId;
    private String translatedText;

    private String audioUrl;

    private String transcriptionAudioUrl;

    public CompletedSentenceItemDto(VoiceEntity voice){
        sentenceId = voice.getTranslatedSentence().getSentenceId();
        sentenceText = voice.getTranslatedSentence().getSentence().getSentenceText();
        translatedSentenceId = voice.getTranslatedSentenceId();;
        translatedText = voice.getTranslatedSentence().getTranslatedText();
        audioUrl = voice.getFileUrl();
    }

    public CompletedSentenceItemDto(SentenceItemDto sentenceItemDto){
        sentenceId = sentenceItemDto.getSentenceId();
        sentenceText = sentenceItemDto.getSentenceText();
        translatedSentenceId = sentenceItemDto.getTranslatedSentenceId();
        translatedText = sentenceItemDto.getTranslatedText();
        audioUrl = sentenceItemDto.getAudioUrl();
        transcriptionAudioUrl = sentenceItemDto.getTranscriptionAudioUrl();
    }

    public CompletedSentenceItemDto(TranslatedSentenceEntity entity){
        sentenceId = entity.getSentenceId();
        sentenceText = entity.getSentence().getSentenceText();
        translatedSentenceId = entity.getTranslatedSentenceId();
        translatedText = entity.getTranslatedText();
    }

    public String getAudioUrl() {
        return audioUrl != null && !audioUrl.isBlank() ? audioUrl : null;
    }
}
