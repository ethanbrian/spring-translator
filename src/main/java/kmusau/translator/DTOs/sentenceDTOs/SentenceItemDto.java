package kmusau.translator.DTOs.sentenceDTOs;


public interface SentenceItemDto {
   Long getSentenceId();
   String getSentenceText();

   String getTranscriptionAudioUrl();
   Long getTranslatedSentenceId();
   String getTranslatedText();

   String getAudioUrl();
}
