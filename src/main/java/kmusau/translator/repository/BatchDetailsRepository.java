package kmusau.translator.repository;

import kmusau.translator.DTOs.sentenceDTOs.SentenceItemDto;
import kmusau.translator.DTOs.stats.TotalSentencesDto;
import kmusau.translator.DTOs.stats.TotalTranslatedSentencesDto;
import kmusau.translator.entity.BatchDetailsEntity;
import kmusau.translator.enums.BatchStatus;
import kmusau.translator.enums.BatchType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BatchDetailsRepository extends JpaRepository<BatchDetailsEntity, Long> {

    List<BatchDetailsEntity> findByTranslatedByIdAndBatchStatus(Long translatorId, BatchStatus batchStatus);
    List<BatchDetailsEntity> findByTranslatedByIdAndBatchDetailsId(Long translatorId, Long batchDetailsId);

    List<BatchDetailsEntity> findByTranslationVerifiedByIdAndBatchStatus(Long reviewerId, BatchStatus batchStatus);

    List<BatchDetailsEntity> findByTranslationVerifiedByIdAndBatchDetailsId(Long reviewerId, Long batchDetailsId);


    List<BatchDetailsEntity> findBySecondReviewerIdAndBatchStatus(Long secondReviewerId, BatchStatus batchStatus);

    List<BatchDetailsEntity> findBySecondReviewerIdAndBatchDetailsId(Long secondReviewerId, Long batchDetailsId);

    List<BatchDetailsEntity> findByRecordedByIdAndBatchStatus(Long recorderId, BatchStatus batchStatus);

    List<BatchDetailsEntity> findByRecordedByIdAndBatchDetailsId(Long recorderId, Long batchDetailsId);

    List<BatchDetailsEntity> findByBatchId(Long batchId);

    List<BatchDetailsEntity> findAllByTranslatedByIdAndBatch_BatchType(Long userId, BatchType batchType);

    List<BatchDetailsEntity> findAllByTranslationVerifiedByIdAndBatch_BatchType(Long userId, BatchType batchType);

    List<BatchDetailsEntity> findAllBySecondReviewerIdAndBatch_BatchType(long userId, BatchType batchType);

    List<BatchDetailsEntity> findAllByRecordedById(long userId);

    List<BatchDetailsEntity> findAllByAudioVerifiedById(long userId);

    List<BatchDetailsEntity> findAllByBatchDetailsIdAndAudioVerifiedById(Long batchDetailsId, Long audioVerifiedById);

    void deleteAllByBatchId(Long batchNo);

    @Query(value = "SELECT COUNT(CASE WHEN (s.sentence_text IS NOT NULL) THEN 1  END) as totalSentences, " +
            "       COUNT(CASE WHEN (s.audio_link IS NOT NULL) THEN 1  END) as totalUploadedAudios " +
            "FROM batches b LEFT JOIN sentences s on b.batch_no = s.batch_no " +
            "where s.deletion_status = 0 AND b.deletion_status = 0", nativeQuery = true)
    TotalSentencesDto getTotalSentences();

    @Query(value = "SELECT count(CASE WHEN (b.batch_type = 'TEXT') THEN 1 END) AS totalTranslatedSentences," +
            "       count(CASE WHEN (b.batch_type = 'AUDIO') THEN 1 END) AS totalTranscribedAudios " +
            "FROM translated_sentence ts " +
            "    CROSS JOIN batch_details bd on bd.batch_details_id = ts.batch_details_id " +
            "    CROSS JOIN batches b on bd.batch_id = b.batch_no " +
            "    WHERE ts.deletion_status = 0 and bd.deletion_status = 0 AND b.deletion_status = 0", nativeQuery = true)
    TotalTranslatedSentencesDto getTotalTranslatedSentences();

    @Query("SELECT bd.batchDetailsId FROM BatchDetailsEntity bd")
    List<Long> findAllBatchDetailsId();

    @Query(value = "SELECT s.sentence_id AS sentenceId, s.sentence_text AS sentenceText, t.translated_sentence_id AS translatedSentenceId, t.translated_text AS translatedText, " +
            "(SELECT v.file_url FROM voice v WHERE v.translated_sentence_id = t.translated_sentence_id ORDER BY v.voice_id DESC LIMIT 1) AS audioUrl  " +
            "FROM translated_sentence t " +
            "CROSS JOIN sentences s ON s.sentence_id = t.sentence_id " +
            "CROSS JOIN batch_details b on b.batch_details_id = t.batch_details_id " +
            "CROSS JOIN  languages l ON l.language_id = b.language " +
            "WHERE l.language_id = :languageId AND b.batch_status >= :batchStatus", nativeQuery = true)
    List<SentenceItemDto> getAllSentencesInLanguagePerBatchDetailsStatus(Long languageId, Integer batchStatus);

    @Query(value = "SELECT s.sentence_id AS sentenceId, s.sentence_text AS sentenceText, s.audio_link AS transcriptionAudioUrl, t.translated_sentence_id AS translatedSentenceId, t.translated_text AS translatedText, " +
            "(SELECT v.file_url FROM voice v WHERE v.translated_sentence_id = t.translated_sentence_id ORDER BY v.voice_id DESC LIMIT 1) AS audioUrl  " +
            "FROM translated_sentence t " +
            "CROSS JOIN sentences s ON s.sentence_id = t.sentence_id " +
            "CROSS JOIN batch_details b on b.batch_details_id = t.batch_details_id " +
            "WHERE b.batch_details_id = :batchDetailsId", nativeQuery = true)
    List<SentenceItemDto> getAllSentencesInBatchDetails(Long batchDetailsId);
}
