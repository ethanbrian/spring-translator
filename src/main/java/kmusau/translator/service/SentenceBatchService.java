package kmusau.translator.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kmusau.translator.DTOs.batchDTO.BatchDto;
import kmusau.translator.DTOs.batchDetails.AddBatchDetailsDto;
import kmusau.translator.DTOs.batchDetails.BatchDetailsDto;
import kmusau.translator.DTOs.batchDetails.BatchInfoDto;
import kmusau.translator.DTOs.batchDetails.BatchInfoItemDTO;
import kmusau.translator.DTOs.batchDetails.BatchInfoStatsDto;
import kmusau.translator.DTOs.sentenceDTOs.CompletedSentenceItemDto;
import kmusau.translator.DTOs.sentenceDTOs.CompletedSentencesDto;
import kmusau.translator.DTOs.sentenceDTOs.ExpertReviewedSentencesDto;
import kmusau.translator.DTOs.sentenceDTOs.SentenceItemDto;
import kmusau.translator.DTOs.stats.BatchDetailsStatsDto;
import kmusau.translator.DTOs.translatedSentencesDTOs.SentenceToReviewDto;
import kmusau.translator.DTOs.translatedSentencesDTOs.TranslatedSentenceItemDto;
import kmusau.translator.entity.BatchDetailsEntity;
import kmusau.translator.entity.BatchDetailsStatsEntity;
import kmusau.translator.entity.BatchEntity;
import kmusau.translator.entity.LanguageEntity;
import kmusau.translator.entity.ModeratorCommentEntity;
import kmusau.translator.entity.TranslatedSentenceEntity;
import kmusau.translator.entity.UsersEntity;
import kmusau.translator.entity.VoiceEntity;
import kmusau.translator.enums.BatchStatus;
import kmusau.translator.enums.BatchType;
import kmusau.translator.enums.StatusTypes;
import kmusau.translator.enums.Task;
import kmusau.translator.repository.BatchDetailsRepository;
import kmusau.translator.repository.BatchDetailsStatsRepository;
import kmusau.translator.repository.BatchRepository;
import kmusau.translator.repository.LanguageRepository;
import kmusau.translator.repository.ModeratorCommentRepo;
import kmusau.translator.repository.SentenceRepository;
import kmusau.translator.repository.TranslatedSentenceRepository;
import kmusau.translator.repository.UserRepository;
import kmusau.translator.repository.VoiceRepository;
import kmusau.translator.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SentenceBatchService {
    public static final String RESPONSE = "response";

    public static final String BATCH_DETAILS = "batchDetails";

    @Autowired
    BatchRepository batchRepo;

    @Autowired
    BatchDetailsRepository batchDetailsRepo;

    @Autowired
    SentenceService sentenceService;

    @Autowired
    SentenceRepository sentenceRepository;

    @Autowired
    TranslatedSentenceRepository translatedSentenceRepo;

    @Autowired
    VoiceRepository voiceRepo;

    @Autowired
    LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModeratorCommentRepo moderatorCommentRepo;

    @Autowired
    private BatchDetailsStatsRepository batchDetailsStatsRepository;

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private AmazonClient amazonClient;

    public ResponseEntity<ResponseMessage> addBatch(BatchDto batchDto) {
        BatchEntity batchEntity = batchDto.dtoToEntity();
        BatchEntity batch = (BatchEntity)this.batchRepo.save(batchEntity);
        return this.sentenceService.addSentences(batchDto.getSentences(), batch.getBatchNo());
    }

    public ResponseEntity editBatch(BatchDto batchDto) {
        if (batchDto.getBatchNo() == null)
            return ResponseEntity.badRequest().body("Please provide old batch no");
        if (batchDto.getSource() == null || batchDto.getSource().isBlank())
            return ResponseEntity.badRequest().body("Please provide source");
        if (batchDto.getDescription() == null || batchDto.getDescription().isBlank())
            return ResponseEntity.badRequest().body("Please provide description");
        if (batchDto.getLinkUrl() == null || batchDto.getLinkUrl().isBlank())
            return ResponseEntity.badRequest().body("Please provide link url");
        Optional<BatchEntity> optionalOldBatch = this.batchRepo.findById(batchDto.getBatchNo());
        if (optionalOldBatch.isEmpty())
            return ResponseEntity.badRequest().body("Sorry, the batch you are trying to edit was not found.");
        BatchEntity batch = optionalOldBatch.get();
        batch.setSource(batchDto.getSource());
        batch.setDescription(batchDto.getDescription());
        batch.setLinkUrl(batch.getLinkUrl());
        this.batchRepo.save(batch);
        return ResponseEntity.ok(batch);
    }

    @Transactional
    public ResponseEntity<ResponseMessage> deleteBatch(Long batchNo) {
        if (batchNo == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide batch no."));
        Optional<BatchEntity> batch = this.batchRepo.findById(batchNo);
        if (batch.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Sorry, the batch you are trying to delete was not found."));
        this.sentenceRepository.deleteAllByBatchNo(batchNo);
        this.translatedSentenceRepo.deleteAllByBatchNumber(batchNo);
        this.batchDetailsStatsRepository.deleteAllByBatchId(batchNo);
        this.batchDetailsRepo.deleteAllByBatchId(batchNo);
        this.batchRepo.deleteById(batchNo);
        return ResponseEntity.ok(new ResponseMessage("Batch successfully deleted"));
    }

    @Transactional
    public ResponseEntity addBatchDetails(AddBatchDetailsDto batchDetailsDto, Long batchNo) {
        if (batchDetailsDto == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide translated by id and language"));
        if (batchDetailsDto.getTranslatedById() == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide translated by id"));
        if (batchDetailsDto.getLanguage() == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide language"));
        Optional<LanguageEntity> optionalLanguageEntity = this.languageRepository.findById(batchDetailsDto.getLanguage());
        if (optionalLanguageEntity.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Language does not exist"));
        Optional<UsersEntity> user = this.userRepository.findById(batchDetailsDto.getTranslatedById());
        if (user.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("User not found"));
        Optional<BatchEntity> optionalBatch = this.batchRepo.findById(batchNo);
        if (optionalBatch.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Batch not found"));
        BatchEntity batchEntity = optionalBatch.get();
        if (batchEntity.getBatchType() == BatchType.AUDIO && !Objects.equals(batchDetailsDto.getLanguage(), batchEntity.getAudioLanguage().getLanguageId()))
            return ResponseEntity.badRequest().body(new ResponseMessage("Wrong language for the Audio Batch. The correct language is " + batchEntity.getAudioLanguage().getName()));
        BatchDetailsEntity batchDetails = new BatchDetailsEntity();
        batchDetails.setTranslatedById(batchDetailsDto.getTranslatedById());
        batchDetails.setTranslatedBy(user.get());
        batchDetails.setLanguage(optionalLanguageEntity.get());
        batchDetails.setBatchId(batchEntity.getBatchNo());
        batchDetails.setBatchStatus(BatchStatus.assignedTranslator);
        batchDetails = (BatchDetailsEntity)this.batchDetailsRepo.save(batchDetails);
        Optional<BatchDetailsStatsEntity> batchDetailsStats = this.batchDetailsStatsRepository.findByBatchDetailsBatchDetailsId(batchDetails.getBatchDetailsId());
        if (batchDetailsStats.isEmpty()) {
            BatchDetailsStatsEntity batchDetailsStatsEntity = new BatchDetailsStatsEntity();
            batchDetailsStatsEntity.setBatchDetails(batchDetails);
            this.batchDetailsStatsRepository.save(batchDetailsStatsEntity);
        }
        return ResponseEntity.ok(batchDetails);
    }

    public List<BatchDetailsDto> getBatchDetailsByBatch(Long batchId) {
        List<BatchDetailsEntity> batchDetails = this.batchDetailsRepo.findByBatchId(batchId);
        List<BatchDetailsDto> batchDtoList = new ArrayList<>();
        for (BatchDetailsEntity batchDetails1 : batchDetails) {
            BatchDetailsDto batchDetailsDto = (new BatchDetailsDto()).entityToDto(batchDetails1);
            batchDtoList.add(batchDetailsDto);
        }
        return batchDtoList;
    }

    public BatchDetailsEntity editBatchDetailsStatus(BatchDetailsEntity batchDetails, Long batchDetailsId) {
        BatchDetailsEntity batchDetails1 = this.batchDetailsRepo.findById(batchDetailsId).get();
        if (Objects.nonNull(batchDetails.getBatchStatus()))
            batchDetails1.setBatchStatus(batchDetails.getBatchStatus());
        return (BatchDetailsEntity)this.batchDetailsRepo.save(batchDetails1);
    }

    public BatchDetailsEntity assignTextVerifier(BatchDetailsEntity batchDetails, Long batchDetailsId) {
        BatchDetailsEntity batchDetails1 = this.batchDetailsRepo.findById(batchDetailsId).get();
        if (Objects.nonNull(batchDetails.getTranslationVerifiedById()))
            batchDetails1.setTranslationVerifiedById(batchDetails.getTranslationVerifiedById());
        batchDetails1.setBatchStatus(BatchStatus.assignedTextVerifier);
        return (BatchDetailsEntity)this.batchDetailsRepo.save(batchDetails1);
    }

    @Transactional
    public BatchDetailsEntity assignExpertReviewer(BatchDetailsEntity batchDetails, Long batchDetailsId) {
        BatchDetailsEntity batchDetails1 = this.batchDetailsRepo.findById(batchDetailsId).get();
        int noOfSentencesToReview = (int)Math.ceil(0.1D * batchDetails1.getTranslatedSentence().size());
        List<TranslatedSentenceEntity> sentencesToReview = batchDetails1.getTranslatedSentence().subList(0, noOfSentencesToReview);
        List<Long> translatedSentencesToReviewIds = (List<Long>)sentencesToReview.stream().map(TranslatedSentenceEntity::getTranslatedSentenceId).collect(Collectors.toList());
        this.translatedSentenceRepo.assignSentencesToExpertReviewer(translatedSentencesToReviewIds);
        if (Objects.nonNull(batchDetails.getSecondReviewerId()))
            batchDetails1.setSecondReviewerId(batchDetails.getSecondReviewerId());
        batchDetails1.setBatchStatus(BatchStatus.assignedExpertReviewer);
        return (BatchDetailsEntity)this.batchDetailsRepo.save(batchDetails1);
    }

    public ResponseEntity assignRecorder(BatchDetailsEntity batchDetails, Long batchDetailsId) {
        Optional<BatchDetailsEntity> optionalBatchDetails = this.batchDetailsRepo.findById(batchDetailsId);
        if (optionalBatchDetails.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Batch details does not exist"));
        if (batchDetails.getRecordedById() == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide user id of the recorder"));
        BatchDetailsEntity batchDetailsEntity = optionalBatchDetails.get();
        if (batchDetailsEntity.getBatch().getBatchType() == BatchType.AUDIO)
            return ResponseEntity.badRequest().body(new ResponseMessage("Audio batches cannot be assigned a recorder"));
        batchDetailsEntity.setRecordedById(batchDetails.getRecordedById());
        batchDetailsEntity.setBatchStatus(BatchStatus.assignedRecorder);
        return ResponseEntity.ok().body(this.batchDetailsRepo.save(batchDetailsEntity));
    }

    public ResponseEntity assignAudioVerifier(BatchDetailsEntity batchDetails, Long batchDetailsId) {
        Optional<BatchDetailsEntity> optionalBatchDetails = this.batchDetailsRepo.findById(batchDetailsId);
        if (optionalBatchDetails.isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Batch details does not exist"));
        if (batchDetails.getAudioVerifiedById() == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide user id of the verifier"));
        BatchDetailsEntity batchDetailsEntity = optionalBatchDetails.get();
        if (batchDetailsEntity.getBatch().getBatchType() == BatchType.AUDIO)
            return ResponseEntity.badRequest().body(new ResponseMessage("Audio batches cannot be assigned an audio verifier"));
        batchDetailsEntity.setAudioVerifiedById(batchDetails.getAudioVerifiedById());
        batchDetailsEntity.setBatchStatus(BatchStatus.assignedAudioVerifier);
        return ResponseEntity.ok().body(this.batchDetailsRepo.save(batchDetailsEntity));
    }

    public ResponseEntity<SentenceToReviewDto> reviewerAssignedTasks(Long reviewerId, BatchStatus batchStatus, Long batchDetailsId) {
        List<BatchDetailsEntity> batchDetails;
        if (batchDetailsId != null) {
            batchDetails = this.batchDetailsRepo.findByTranslationVerifiedByIdAndBatchDetailsId(reviewerId, batchDetailsId);
        } else {
            batchDetails = this.batchDetailsRepo.findByTranslationVerifiedByIdAndBatchStatus(reviewerId, batchStatus);
        }
        batchDetailsId = null;
        List<TranslatedSentenceItemDto> unreviewedSentencesDto = new ArrayList<>();
        List<TranslatedSentenceItemDto> reviewedSentencesDto = new ArrayList<>();
        String language = null;
        String batchType = null;
        if (!batchDetails.isEmpty())
            for (BatchDetailsEntity aBatchDetail : batchDetails) {
                batchDetailsId = aBatchDetail.getBatchDetailsId();
                language = aBatchDetail.getLanguage().getName();
                batchType = aBatchDetail.getBatch().getBatchType().getName();
                List<TranslatedSentenceEntity> unreviewedSentences = this.translatedSentenceRepo.findUnreviewedByTranslationVerifiedByIdAndBatchDetailsId(reviewerId, batchDetailsId);
                List<TranslatedSentenceEntity> reviewedSentences = this.translatedSentenceRepo.findReviewedByTranslationVerifiedByIdAndBatchDetailsId(reviewerId, batchDetailsId);
                unreviewedSentencesDto = getTranslatedSentenceItemDtos(unreviewedSentences, Boolean.valueOf(false), null);
                reviewedSentencesDto = getTranslatedSentenceItemDtos(reviewedSentences, Boolean.valueOf(true), null);
                if (!unreviewedSentences.isEmpty())
                    break;
            }
        SentenceToReviewDto sentenceToReviewDto = new SentenceToReviewDto();
        sentenceToReviewDto.setBatchDetailsId(batchDetailsId);
        sentenceToReviewDto.setLanguage(language);
        sentenceToReviewDto.setUnreviewedSentences(unreviewedSentencesDto);
        sentenceToReviewDto.setReviewedSentences(reviewedSentencesDto);
        sentenceToReviewDto.setBatchType(batchType);
        return ResponseEntity.ok(sentenceToReviewDto);
    }

    public ResponseEntity<SentenceToReviewDto> expertReviewerAssignedTasks(Long reviewerId, BatchStatus batchStatus, Long batchDetailsId) {
        List<BatchDetailsEntity> batchDetails;
        if (batchDetailsId != null) {
            batchDetails = this.batchDetailsRepo.findBySecondReviewerIdAndBatchDetailsId(reviewerId, batchDetailsId);
        } else {
            batchDetails = this.batchDetailsRepo.findBySecondReviewerIdAndBatchStatus(reviewerId, batchStatus);
        }
        Long batchDetailId = null;
        List<TranslatedSentenceItemDto> unreviewedTranslatedSentencesDto = new ArrayList<>();
        List<TranslatedSentenceItemDto> reviewedTranslatedSentencesDto = new ArrayList<>();
        String language = null;
        String batchType = null;
        if (!batchDetails.isEmpty())
            for (BatchDetailsEntity aBatchDetail : batchDetails) {
                batchDetailId = aBatchDetail.getBatchDetailsId();
                language = aBatchDetail.getLanguage().getName();
                batchType = aBatchDetail.getBatch().getBatchType().getName();
                List<TranslatedSentenceEntity> unreviewedTranslatedSentences = this.translatedSentenceRepo.findExpertReviewersUnreviewedTasks(reviewerId, batchDetailId);
                List<TranslatedSentenceEntity> reviewedTranslatedSentences = this.translatedSentenceRepo.findExpertReviewersReviewedTasks(reviewerId, batchDetailId);
                unreviewedTranslatedSentencesDto = getTranslatedSentenceItemDtos(unreviewedTranslatedSentences, null, Boolean.valueOf(false));
                reviewedTranslatedSentencesDto = getTranslatedSentenceItemDtos(reviewedTranslatedSentences, null, Boolean.valueOf(true));
                if (!unreviewedTranslatedSentences.isEmpty())
                    break;
            }
        SentenceToReviewDto sentenceToReviewDto = new SentenceToReviewDto();
        sentenceToReviewDto.setBatchDetailsId(batchDetailId);
        sentenceToReviewDto.setLanguage(language);
        sentenceToReviewDto.setUnreviewedSentences(unreviewedTranslatedSentencesDto);
        sentenceToReviewDto.setReviewedSentences(reviewedTranslatedSentencesDto);
        sentenceToReviewDto.setBatchType(batchType);
        return ResponseEntity.ok(sentenceToReviewDto);
    }

    public BatchInfoDto getTranslatorBatchDetails(Long userId) {
        List<BatchDetailsEntity> translationBatchDetails = this.batchDetailsRepo.findAllByTranslatedByIdAndBatch_BatchType(userId, BatchType.TEXT);
        List<BatchInfoItemDTO> sortedTranslationBatchDetails = getSortedTranslationBatchDetails(translationBatchDetails);
        List<BatchDetailsEntity> transcriptionBatchDetails = this.batchDetailsRepo.findAllByTranslatedByIdAndBatch_BatchType(userId, BatchType.AUDIO);
        List<BatchInfoItemDTO> sortedTranscriptionBatchDetails = getSortedTranslationBatchDetails(transcriptionBatchDetails);
        return new BatchInfoDto(sortedTranslationBatchDetails, sortedTranscriptionBatchDetails);
    }

    public BatchInfoDto getReviewerBatchDetails(Long userId) {
        List<BatchDetailsEntity> translationBatchDetails = this.batchDetailsRepo.findAllByTranslationVerifiedByIdAndBatch_BatchType(userId, BatchType.TEXT);
        List<BatchInfoItemDTO> sortedTranslationBatchDetails = getSortedReviewerBatchDetails(translationBatchDetails);
        List<BatchDetailsEntity> transcriptionBatchDetails = this.batchDetailsRepo.findAllByTranslationVerifiedByIdAndBatch_BatchType(userId, BatchType.AUDIO);
        List<BatchInfoItemDTO> sortedTranscriptionBatchDetails = getSortedReviewerBatchDetails(transcriptionBatchDetails);
        return new BatchInfoDto(sortedTranslationBatchDetails, sortedTranscriptionBatchDetails);
    }

    public BatchInfoDto getExpertReviewerBatchDetails(Long userId) {
        List<BatchDetailsEntity> translationBatchDetails = this.batchDetailsRepo.findAllBySecondReviewerIdAndBatch_BatchType(userId.longValue(), BatchType.TEXT);
        List<BatchInfoItemDTO> sortedTranslationBatchDetails = getSortedExpertReviewerBatchDetails(translationBatchDetails);
        List<BatchDetailsEntity> transcriptionBatchDetails = this.batchDetailsRepo.findAllBySecondReviewerIdAndBatch_BatchType(userId.longValue(), BatchType.AUDIO);
        List<BatchInfoItemDTO> sortedTranscriptionBatchDetails = getSortedExpertReviewerBatchDetails(transcriptionBatchDetails);
        return new BatchInfoDto(sortedTranslationBatchDetails, sortedTranscriptionBatchDetails);
    }

    public BatchInfoDto getAudioRecorderBatchDetails(Long userId) {
        List<BatchDetailsEntity> batchDetails = this.batchDetailsRepo.findAllByRecordedById(userId.longValue());
        List<BatchInfoItemDTO> sortedBatchDetails = (List<BatchInfoItemDTO>)batchDetails.stream().map(element -> {
            Integer rejectedAudios = this.voiceRepo.countAllByStatusAndTranslatedSentenceBatchDetailsId(StatusTypes.rejected, element.getBatchDetailsId());
            BatchInfoItemDTO batchInfoItemDTO = new BatchInfoItemDTO(element);
            if (batchInfoItemDTO.getAudioRecorded().booleanValue()) {
                batchInfoItemDTO.setPendingSentences(rejectedAudios);
                batchInfoItemDTO.setAudioRecorded(Boolean.valueOf((rejectedAudios.intValue() <= 0)));
            }
            return batchInfoItemDTO;
        }).sorted((e1, e2) -> (!e1.getAudioRecorded().booleanValue() && e2.getAudioRecorded().booleanValue()) ? -1 : ((e1.getAudioRecorded().booleanValue() && !e2.getAudioRecorded().booleanValue()) ? 1 : 0)).collect(Collectors.toList());
        return new BatchInfoDto(sortedBatchDetails, null);
    }

    public BatchInfoDto getAudioReviewerBatchDetails(Long userId) {
        List<BatchDetailsEntity> batchDetails = this.batchDetailsRepo.findAllByAudioVerifiedById(userId.longValue());
        List<BatchInfoItemDTO> sortedBatchDetails = (List<BatchInfoItemDTO>)batchDetails.stream().map(element -> {
            Integer unreviewedAudios = this.voiceRepo.countAllByStatusAndTranslatedSentenceBatchDetailsId(StatusTypes.unreviewed, element.getBatchDetailsId());
            BatchInfoItemDTO batchInfoItemDTO = new BatchInfoItemDTO(element);
            if (batchInfoItemDTO.getAudioReviewed().booleanValue())
                batchInfoItemDTO.setAudioRecorded(Boolean.valueOf((unreviewedAudios.intValue() <= 0)));
            batchInfoItemDTO.setPendingSentences(unreviewedAudios);
            return batchInfoItemDTO;
        }).sorted((e1, e2) -> (!e1.getAudioReviewed().booleanValue() && e2.getAudioReviewed().booleanValue()) ? -1 : ((e1.getAudioReviewed().booleanValue() && !e2.getAudioReviewed().booleanValue()) ? 1 : 0)).collect(Collectors.toList());
        return new BatchInfoDto(sortedBatchDetails, null);
    }

    public BatchInfoDto getBatchDetailsByTask(Long userId, Task task) {
        switch (task) {
            case translation:
                return getTranslatorBatchDetails(userId);
            case review:
                return getReviewerBatchDetails(userId);
            case expertReview:
                return getExpertReviewerBatchDetails(userId);
            case audioRecording:
                return getAudioRecorderBatchDetails(userId);
            case audioReviewing:
                return getAudioReviewerBatchDetails(userId);
        }
        return new BatchInfoDto();
    }

    public ResponseEntity getCompletedSentencesPerBatchDetails(Long batchDetailsId) {
        if (batchDetailsId == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide batch details id"));
        Optional<BatchDetailsEntity> optionalBatchDetails = this.batchDetailsRepo.findById(batchDetailsId);
        if (optionalBatchDetails.isEmpty())
            return ResponseEntity.notFound().build();
        BatchDetailsEntity batchDetails = optionalBatchDetails.get();
        String language = batchDetails.getLanguage().getName();
        Integer numberOfAllSentences = this.sentenceRepository.countAllByBatchNo(batchDetails.getBatch().getBatchNo());
        List<VoiceEntity> approvedVoices = this.voiceRepo.findAllByStatusAndTranslatedSentenceBatchDetailsId(StatusTypes.approved, batchDetailsId);
        List<CompletedSentenceItemDto> completedSentenceList = (List<CompletedSentenceItemDto>)approvedVoices.stream().map(voice -> {
            String presignedUrl = this.amazonClient.generatePresignedUrl(voice.getFileUrl());
            voice.setFileUrl(presignedUrl);
            return new CompletedSentenceItemDto(voice);
        }).collect(Collectors.toList());
        CompletedSentencesDto completedSentencesDto = new CompletedSentencesDto();
        completedSentencesDto.setBatchDetailsId(batchDetailsId);
        completedSentencesDto.setLanguage(language);
        completedSentencesDto.setNumberOfSentences(numberOfAllSentences);
        completedSentencesDto.setNumberOfCompletedSentences(Integer.valueOf(approvedVoices.size()));
        completedSentencesDto.setCompletedSentences(completedSentenceList);
        return ResponseEntity.ok(completedSentencesDto);
    }

    public ResponseEntity<List<BatchInfoStatsDto>> getBatchStats() {
        List<BatchDetailsEntity> batchDetailsList = this.batchDetailsRepo.findAll(
                Sort.by(Sort.Direction.ASC, new String[] { "batch.source" }));
        List<BatchInfoStatsDto> batchInfoStats = (List<BatchInfoStatsDto>)batchDetailsList.stream().map(batchDetail -> {
            Integer numberOfAllSentences = this.sentenceRepository.countAllByBatchNo(batchDetail.getBatch().getBatchNo());
            BatchDetailsStatsDto batchDetailsStatsDto = this.translatedSentenceRepo.getBatchDetailsStats(batchDetail.getBatchDetailsId());
            return BatchInfoStatsDto.entityToDto(batchDetail, numberOfAllSentences, batchDetailsStatsDto);
        }).collect(Collectors.toList());
        return ResponseEntity.ok(batchInfoStats);
    }

    public ResponseEntity getTranslatedSentences(Long batchDetailsId) {
        if (batchDetailsId == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide batch details id"));
        Optional<BatchDetailsEntity> optionalBatchDetails = this.batchDetailsRepo.findById(batchDetailsId);
        if (optionalBatchDetails.isEmpty())
            return ResponseEntity.notFound().build();
        BatchDetailsEntity batchDetails = optionalBatchDetails.get();
        BatchType batchType = batchDetails.getBatch().getBatchType();
        List<CompletedSentenceItemDto> sentences = getSentencesWithPresignedAudioUrl(this.batchDetailsRepo.getAllSentencesInBatchDetails(batchDetailsId));
        CompletedSentencesDto completedSentencesDto = new CompletedSentencesDto();
        completedSentencesDto.setBatchDetailsId(batchDetailsId);
        completedSentencesDto.setBatchType(batchType.getName());
        completedSentencesDto.setBatchDetailsStatus(batchDetails.getBatchStatus().getLabel(batchType));
        completedSentencesDto.setLanguage(batchDetails.getLanguage().getName());
        completedSentencesDto.setNumberOfSentences(Integer.valueOf(sentences.size()));
        completedSentencesDto.setNumberOfCompletedSentences(Integer.valueOf(sentences.size()));
        completedSentencesDto.setCompletedSentences(sentences);
        return ResponseEntity.ok(completedSentencesDto);
    }

    private List<TranslatedSentenceItemDto> getTranslatedSentenceItemDtos(List<TranslatedSentenceEntity> translatedSentences, Boolean isFirstReviewed, Boolean isExpertReviewed) {
        List<TranslatedSentenceItemDto> unreviewedTranslatedSentencesDto = (List<TranslatedSentenceItemDto>)translatedSentences.stream().map(translatedSentence -> {
            Boolean isAccepted = null;
            if (isFirstReviewed != null && isFirstReviewed.booleanValue()) {
                isAccepted = Boolean.valueOf((translatedSentence.getReviewStatus().ordinal() == 0));
            } else if (isExpertReviewed != null && isExpertReviewed.booleanValue()) {
                isAccepted = Boolean.valueOf((translatedSentence.getSecondReview().ordinal() == 0));
            }
            ModeratorCommentEntity moderatorCommentEntity = this.moderatorCommentRepo.findAllByTranslatedSentence_TranslatedSentenceId(translatedSentence.getTranslatedSentenceId());
            String moderatorComment = "";
            if (moderatorCommentEntity != null)
                moderatorComment = moderatorCommentEntity.getComment();
            if (translatedSentence.getBatchDetails().getBatch().getBatchType() == BatchType.AUDIO)
                translatedSentence.getSentence().setAudioLink(this.amazonClient.generatePresignedUrl(translatedSentence.getSentence().getAudioLink()));
            return TranslatedSentenceItemDto.entityToDto(translatedSentence, moderatorComment, isAccepted);
        }).collect(Collectors.toList());
        return unreviewedTranslatedSentencesDto;
    }

    @Transactional
    public ResponseEntity<ResponseMessage> markTranslationAsComplete(Long batchDetailsId) {
        HashMap<String, Object> result = getBatchDetails(batchDetailsId);
        ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
        if (response != null)
            return response;
        BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
        if (batchDetails == null)
            return ResponseEntity.internalServerError().body(new ResponseMessage("An error occured"));
        if (batchDetails.getBatchStatus().ordinal() < BatchStatus.translated.ordinal()) {
            batchDetails.setBatchStatus(BatchStatus.translated);
            this.batchDetailsRepo.save(batchDetails);
        }
        return ResponseEntity.ok(new ResponseMessage("Translations marked as complete"));
    }

    @Transactional
    public ResponseEntity<ResponseMessage> markModerationAsComplete(Long batchDetailsId) {
        HashMap<String, Object> result = getBatchDetails(batchDetailsId);
        ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
        if (response != null)
            return response;
        BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
        if (batchDetails == null)
            return ResponseEntity.internalServerError().body(new ResponseMessage("An error occurred"));
        if (batchDetails.getBatchStatus().ordinal() < BatchStatus.translationVerified.ordinal()) {
            Optional<BatchDetailsStatsEntity> optionalTranslatorStats = this.batchDetailsStatsRepository.findByBatchDetailsBatchDetailsId(batchDetailsId);
            if (optionalTranslatorStats.isPresent()) {
                Integer rejectedSentences = this.translatedSentenceRepo.countAllByBatchDetailsIdAndReviewStatus(batchDetailsId, StatusTypes.rejected);
                if (rejectedSentences.intValue() <= 0) {
                    batchDetails.setBatchStatus(BatchStatus.translationVerified);
                    this.batchDetailsRepo.save(batchDetails);
                }
            }
        }
        return ResponseEntity.ok(new ResponseMessage("Translations marked as verified"));
    }

    public ResponseEntity getAffectedBatcheDetails() {
        List<Long> batchDetailsIds = this.batchDetailsRepo.findAllBatchDetailsId();
        ArrayList<Long> malformedBatchDetailsIds = new ArrayList<>();
        for (Long batchDetailsId : batchDetailsIds) {
            HashMap<String, Object> result = getBatchDetails(batchDetailsId);
            ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
            if (response != null)
                return response;
            BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
            if (batchDetails == null)
                return ResponseEntity.internalServerError().body(new ResponseMessage("An error occurred"));
            if (batchDetails.getBatchStatus().ordinal() < BatchStatus.translationVerified.ordinal() && batchDetails.getBatchStatus().ordinal() >= BatchStatus.translated.ordinal()) {
                Optional<BatchDetailsStatsEntity> optionalTranslatorStats = this.batchDetailsStatsRepository.findByBatchDetailsBatchDetailsId(batchDetailsId);
                if (optionalTranslatorStats.isPresent()) {
                    Integer approvedSentences = this.translatedSentenceRepo.countAllByBatchDetailsIdAndReviewStatus(batchDetailsId, StatusTypes.approved);
                    Integer totalSentences = this.translatedSentenceRepo.countAllByBatchDetailsBatchDetailsId(batchDetailsId);
                    if (approvedSentences.intValue() >= totalSentences.intValue())
                        malformedBatchDetailsIds.add(batchDetails.getBatchDetailsId());
                }
            }
        }
        return ResponseEntity.ok(malformedBatchDetailsIds);
    }

    @Transactional
    public ResponseEntity<ResponseMessage> markExpertVerificationAsComplete(Long batchDetailsId) {
        HashMap<String, Object> result = getBatchDetails(batchDetailsId);
        ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
        if (response != null)
            return response;
        BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
        if (batchDetails == null)
            return ResponseEntity.internalServerError().body(new ResponseMessage("An error occurred"));
        if (batchDetails.getBatchStatus().ordinal() < BatchStatus.secondVerificationDone.ordinal()) {
            Optional<BatchDetailsStatsEntity> optionalUsersStats = this.batchDetailsStatsRepository.findByBatchDetailsBatchDetailsId(batchDetailsId);
            if (optionalUsersStats.isPresent()) {
                Integer rejectedSentences = this.translatedSentenceRepo.countAllByBatchDetailsIdAndSecondReview(batchDetailsId, StatusTypes.rejected);
                if (rejectedSentences.intValue() <= 0) {
                    batchDetails.setBatchStatus(BatchStatus.secondVerificationDone);
                    this.batchDetailsRepo.save(batchDetails);
                }
            }
        }
        return ResponseEntity.ok(new ResponseMessage("Translations marked as expert verified"));
    }

    @Transactional
    public ResponseEntity<ResponseMessage> markBatchAsRecorded(Long batchDetailsId) {
        HashMap<String, Object> result = getBatchDetails(batchDetailsId);
        ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
        if (response != null)
            return response;
        BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
        if (batchDetails == null)
            return ResponseEntity.internalServerError().body(new ResponseMessage("An error occurred"));
        if (batchDetails.getBatchStatus().ordinal() < BatchStatus.recorded.ordinal()) {
            List<TranslatedSentenceEntity> voiceTasks = this.voiceService.findVoiceTasks(batchDetailsId);
            if (voiceTasks.isEmpty()) {
                batchDetails.setBatchStatus(BatchStatus.recorded);
                this.batchDetailsRepo.save(batchDetails);
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.ok(new ResponseMessage("Translations marked as recorded"));
    }

    @Transactional
    public ResponseEntity<ResponseMessage> markAudioReviewAsComplete(Long batchDetailsId) {
        HashMap<String, Object> result = getBatchDetails(batchDetailsId);
        ResponseEntity<ResponseMessage> response = (ResponseEntity<ResponseMessage>)result.get("response");
        if (response != null)
            return response;
        BatchDetailsEntity batchDetails = (BatchDetailsEntity)result.get("batchDetails");
        if (batchDetails == null)
            return ResponseEntity.internalServerError().body(new ResponseMessage("An error occurred"));
        if (batchDetails.getBatchStatus().ordinal() < BatchStatus.audioVerified.ordinal()) {
            batchDetails.setBatchStatus(BatchStatus.audioVerified);
            this.batchDetailsRepo.save(batchDetails);
        }
        return ResponseEntity.ok(new ResponseMessage("Audios marked as verified"));
    }

    public HashMap<String, Object> getBatchDetails(Long batchDetailsId) {
        HashMap<String, Object> result = new HashMap<>();
        if (batchDetailsId == null) {
            result.put("response", ResponseEntity.badRequest().body(new ResponseMessage("Please provide batch details id")));
            return result;
        }
        Optional<BatchDetailsEntity> optionalBatchDetails = this.batchDetailsRepo.findById(batchDetailsId);
        if (optionalBatchDetails.isEmpty()) {
            result.put("response", ResponseEntity.badRequest().body(new ResponseMessage("Batch details not found")));
        } else {
            result.put("batchDetails", optionalBatchDetails.get());
        }
        return result;
    }

    @Transactional
    public ResponseEntity<ResponseMessage> deleteBatchDetails(Long batchDetailsId) {
        if (batchDetailsId == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Batch details id is required"));
        if (this.batchDetailsRepo.findById(batchDetailsId).isEmpty())
            return ResponseEntity.badRequest().body(new ResponseMessage("Batch details does not exist"));
        this.translatedSentenceRepo.deleteAllByBatchDetailsBatchDetailsId(batchDetailsId);
        this.batchDetailsStatsRepository.deleteAllByBatchDetailsBatchDetailsId(batchDetailsId);
        this.batchDetailsRepo.deleteById(batchDetailsId);
        return ResponseEntity.ok(new ResponseMessage("Batch details successfully deleted"));
    }

    public ResponseEntity<Object> getExpertReviewedSentences(Long languageId) {
        if (languageId == null)
            return ResponseEntity.badRequest().body(new ResponseMessage("Language Id is required"));
        Optional<LanguageEntity> language = this.languageRepository.findById(languageId);
        if (language.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseMessage("Language Id not found"));
        List<CompletedSentenceItemDto> expertReviewedSentences = getSentencesWithPresignedAudioUrl(this.batchDetailsRepo.getAllSentencesInLanguagePerBatchDetailsStatus(languageId, Integer.valueOf(BatchStatus.secondVerificationDone.ordinal())));
        return ResponseEntity.ok().body(new ExpertReviewedSentencesDto(((LanguageEntity)language
                .get()).getName(), expertReviewedSentences));
    }

    private List<CompletedSentenceItemDto> getSentencesWithPresignedAudioUrl(List<SentenceItemDto> sentences) {
        List<CompletedSentenceItemDto> sentencesWithPresignedAudioUrl = (List<CompletedSentenceItemDto>)sentences.stream().map(CompletedSentenceItemDto::new).collect(Collectors.toList());
        sentencesWithPresignedAudioUrl.forEach(sentence -> {
            if (sentence.getAudioUrl() != null) {
                String presignedUrl = this.amazonClient.generatePresignedUrl(sentence.getAudioUrl());
                sentence.setAudioUrl(presignedUrl);
            }
            if (sentence.getTranscriptionAudioUrl() != null) {
                String presignedUrl = this.amazonClient.generatePresignedUrl(sentence.getTranscriptionAudioUrl());
                sentence.setTranscriptionAudioUrl(presignedUrl);
            }
        });
        return sentencesWithPresignedAudioUrl;
    }

    private List<BatchInfoItemDTO> getSortedTranslationBatchDetails(List<BatchDetailsEntity> batchDetails) {
        return (List<BatchInfoItemDTO>)batchDetails.stream()
                .map(element -> {
                    Integer rejectedSentences = this.translatedSentenceRepo.countRejectedSentences(element.getBatchDetailsId(), StatusTypes.rejected, StatusTypes.rejected);
                    BatchInfoItemDTO batchInfoItemDTO = new BatchInfoItemDTO(element);
                    if (batchInfoItemDTO.getTranslated().booleanValue()) {
                        batchInfoItemDTO.setPendingSentences(rejectedSentences);
                        batchInfoItemDTO.setTranslated(Boolean.valueOf((rejectedSentences.intValue() <= 0)));
                    }
                    return batchInfoItemDTO;
                }).sorted((e1, e2) ->
                        (!e1.getTranslated().booleanValue() && e2.getTranslated().booleanValue()) ? -1 : (

                                (e1.getTranslated().booleanValue() && !e2.getTranslated().booleanValue()) ? 1 : 0))

                .collect(Collectors.toList());
    }

    private List<BatchInfoItemDTO> getSortedReviewerBatchDetails(List<BatchDetailsEntity> translationBatchDetails) {
        return (List<BatchInfoItemDTO>)translationBatchDetails.stream()
                .map(element -> {
                    Integer unreviewedSentences = this.translatedSentenceRepo.countAllByBatchDetailsIdAndReviewStatus(element.getBatchDetailsId(), StatusTypes.unreviewed);
                    BatchInfoItemDTO batchInfoItemDTO = new BatchInfoItemDTO(element);
                    batchInfoItemDTO.setReviewed(Boolean.valueOf((unreviewedSentences.intValue() <= 0)));
                    batchInfoItemDTO.setPendingSentences(unreviewedSentences);
                    return batchInfoItemDTO;
                }).sorted((e1, e2) ->
                        (!e1.getReviewed().booleanValue() && e2.getReviewed().booleanValue()) ? -1 : (

                                (e1.getReviewed().booleanValue() && !e2.getReviewed().booleanValue()) ? 1 : 0))

                .collect(Collectors.toList());
    }

    private List<BatchInfoItemDTO> getSortedExpertReviewerBatchDetails(List<BatchDetailsEntity> translationBatchDetails) {
        return (List<BatchInfoItemDTO>)translationBatchDetails.stream()
                .map(element -> {
                    Integer unreviewedSentences = this.translatedSentenceRepo.countAllByBatchDetailsIdAndSecondReview(element.getBatchDetailsId(), StatusTypes.unreviewed);
                    BatchInfoItemDTO batchInfoItemDTO = new BatchInfoItemDTO(element);
                    batchInfoItemDTO.setExpertReviewed(Boolean.valueOf((unreviewedSentences.intValue() <= 0)));
                    batchInfoItemDTO.setPendingSentences(unreviewedSentences);
                    return batchInfoItemDTO;
                }).sorted((e1, e2) ->
                        (!e1.getExpertReviewed().booleanValue() && e2.getExpertReviewed().booleanValue()) ? -1 : (

                                (e1.getExpertReviewed().booleanValue() && !e2.getExpertReviewed().booleanValue()) ? 1 : 0))

                .collect(Collectors.toList());
    }
}
