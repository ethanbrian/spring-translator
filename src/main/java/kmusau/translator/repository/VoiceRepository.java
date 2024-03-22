package kmusau.translator.repository;

import java.util.Date;
import java.util.List;

import kmusau.translator.entity.TranslatedSentenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import kmusau.translator.entity.VoiceEntity;
import kmusau.translator.enums.StatusTypes;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoiceRepository extends JpaRepository<VoiceEntity, Long>{

	List<VoiceEntity> findByStatus(StatusTypes status);
	List<VoiceEntity> findByStatusAndTranslatedSentenceBatchDetailsAudioVerifiedById(StatusTypes status, Long reviewedBy);

	@Query("SELECT v FROM VoiceEntity v WHERE v.translatedSentence.batchDetails.translationVerifiedById = :reviewerId AND " +
			"v.translatedSentence.batchDetails.batchDetailsId = :batchDetailsId AND v.status = 1")
	List<VoiceEntity> findUnreviewedAudios(Long reviewerId, Long batchDetailsId);

	@Query("SELECT v FROM VoiceEntity v WHERE v.translatedSentence.batchDetails.translationVerifiedById = :reviewerId AND " +
			"v.translatedSentence.batchDetails.batchDetailsId = :batchDetailsId AND (v.status = 0 OR v.status = 2)")
	List<VoiceEntity> findReviewedAudios(Long reviewerId, Long batchDetailsId);

	@Query("select v from VoiceEntity v where v.translatedSentence.assignedRecorderId =:recorderId and v.dateCreated between :startDate and :endDate")
	List<VoiceEntity> usersAudiosDone(
			@Param("recorderId") Long recorderId,
			@Param("startDate") Date startDate,
			@Param("endDate") Date endDate
			);

	List<VoiceEntity> findAllByTranslatedSentenceBatchDetails_BatchDetailsId(Long batchDetailsId);

	int countAllByTranslatedSentenceBatchDetails_BatchDetailsId(Long batchDetailsId);

	List<VoiceEntity> findAllByStatusAndTranslatedSentenceBatchDetailsId(StatusTypes statusTypes, Long batchDetailsId);

	Integer countAllByStatusAndTranslatedSentenceBatchDetailsId(StatusTypes statusTypes, Long batchDetailsId);


}
