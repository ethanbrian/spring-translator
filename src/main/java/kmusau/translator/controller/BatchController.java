package kmusau.translator.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kmusau.translator.DTOs.batchDTO.BatchDto;
import kmusau.translator.DTOs.batchDetails.AddBatchDetailsDto;
import kmusau.translator.DTOs.batchDetails.BatchDetailsDto;
import kmusau.translator.DTOs.batchDetails.BatchInfoDto;
import kmusau.translator.DTOs.batchDetails.BatchInfoStatsDto;
import kmusau.translator.entity.BatchDetailsEntity;
import kmusau.translator.enums.BatchType;
import kmusau.translator.enums.Task;
import kmusau.translator.repository.BatchDetailsRepository;
import kmusau.translator.repository.BatchRepository;
import kmusau.translator.repository.TranslatedSentenceRepository;
import kmusau.translator.response.ResponseMessage;
import kmusau.translator.service.AmazonClient;
import kmusau.translator.service.SentenceBatchService;
import kmusau.translator.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatchController {
    @Autowired
    BatchRepository batchRepo;

    @Autowired
    BatchDetailsRepository batchDetailsRepo;

    @Autowired
    TranslatedSentenceRepository translatedSentenceRepository;

    @Autowired
    SentenceBatchService batchService;

    @Autowired
    VoiceService voiceService;

    @Autowired
    AmazonClient amazonClient;

    @GetMapping({"/all/batches"})
    public ResponseEntity getAllBatches(String batchType) {
        BatchType batchTypeEnum;
        Optional<BatchType> batchTypeOptional = BatchType.fromName(batchType);
        if (batchTypeOptional.isEmpty()) {
            batchTypeEnum = BatchType.TEXT;
        } else {
            batchTypeEnum = batchTypeOptional.get();
        }
        return ResponseEntity.ok(this.batchRepo.findAllByBatchType(batchTypeEnum)
                .stream()
                .map(kmusau.translator.DTOs.batchDTO.BatchResponseDto::new)
                .collect(Collectors.toList()));
    }

    @PostMapping({"/batch"})
    public ResponseEntity<ResponseMessage> addBatch(@RequestBody BatchDto batchDto) {
        return this.batchService.addBatch(batchDto);
    }

    @PutMapping({"/batch"})
    public ResponseEntity editBatch(@RequestBody BatchDto batchDto) {
        return this.batchService.editBatch(batchDto);
    }

    @DeleteMapping({"/batch"})
    public ResponseEntity<ResponseMessage> deleteBatch(Long batchNo) {
        return this.batchService.deleteBatch(batchNo);
    }

    @DeleteMapping({"/batch-details"})
    public ResponseEntity<ResponseMessage> deleteBatchDetails(Long batchDetailsId) {
        return this.batchService.deleteBatchDetails(batchDetailsId);
    }

    @GetMapping({"/all/batch-details"})
    public List<BatchDetailsEntity> getAllBatchDetails() {
        return this.batchDetailsRepo.findAll();
    }

    @GetMapping({"/batch-details/{batchId}"})
    public List<BatchDetailsDto> getBatchDetailsByBatch(@PathVariable Long batchId) {
        return this.batchService.getBatchDetailsByBatch(batchId);
    }

    @PostMapping({"/add/batch-details/{batchNo}"})
    public ResponseEntity addBatchDetails(@RequestBody AddBatchDetailsDto batchDetailsDto, @PathVariable Long batchNo) {
        return this.batchService.addBatchDetails(batchDetailsDto, batchNo);
    }

    @PutMapping({"/"})
    public BatchDetailsEntity editBatchDetailsStatus(@RequestBody BatchDetailsEntity batchDetails, @PathVariable Long batchDetailsId) {
        return this.batchService.editBatchDetailsStatus(batchDetails, batchDetailsId);
    }

    @PutMapping({"/assign/text-verifier/{batchDetailsId}"})
    public BatchDetailsEntity assignTextVerifier(@RequestBody BatchDetailsEntity batchDetails, @PathVariable Long batchDetailsId) {
        return this.batchService.assignTextVerifier(batchDetails, batchDetailsId);
    }

    @PutMapping({"/assign/second-reviewer/{batchDetailsId}"})
    public BatchDetailsEntity assignExpertReviewer(@RequestBody BatchDetailsEntity batchDetails, @PathVariable Long batchDetailsId) {
        return this.batchService.assignExpertReviewer(batchDetails, batchDetailsId);
    }

    @PutMapping({"/assign/recorder/{batchDetailsId}"})
    public ResponseEntity assignRecorder(@RequestBody BatchDetailsEntity batchDetails, @PathVariable Long batchDetailsId) {
        return this.batchService.assignRecorder(batchDetails, batchDetailsId);
    }

    @PutMapping({"/assign/audio-verifier/{batchDetailsId}"})
    public ResponseEntity assignAudioVerifier(@RequestBody BatchDetailsEntity batchDetails, @PathVariable Long batchDetailsId) {
        return this.batchService.assignAudioVerifier(batchDetails, batchDetailsId);
    }

    @PutMapping({"/batch-status/translated/{batchDetailsId}"})
    public ResponseEntity<ResponseMessage> batchStatusTranslated(@PathVariable Long batchDetailsId) {
        return this.batchService.markTranslationAsComplete(batchDetailsId);
    }

    @PutMapping({"/batch-status/textVerified/{batchDetailsId}"})
    public ResponseEntity<ResponseMessage> textVerified(@PathVariable Long batchDetailsId) {
        return this.batchService.markModerationAsComplete(batchDetailsId);
    }

    @GetMapping({"/affected-batch-details"})
    public ResponseEntity getAffectedBatchDetails() {
        return this.batchService.getAffectedBatcheDetails();
    }

    @PutMapping({"/batch-status/secondVerification/{batchDetailsId}"})
    public ResponseEntity<ResponseMessage> markSecondVerification(@PathVariable Long batchDetailsId) {
        return this.batchService.markExpertVerificationAsComplete(batchDetailsId);
    }

    @PutMapping({"/batch-status/recorded/{batchDetailsId}"})
    public ResponseEntity<ResponseMessage> batchStatusRecorded(@PathVariable Long batchDetailsId) {
        return this.batchService.markBatchAsRecorded(batchDetailsId);
    }

    @PutMapping({"/batch-status/audioVerified/{batchDetailsId}"})
    public ResponseEntity<ResponseMessage> batchStatusAudioVerified(@PathVariable Long batchDetailsId) {
        return this.batchService.markAudioReviewAsComplete(batchDetailsId);
    }

    @GetMapping({"/user-batch-details"})
    public BatchInfoDto getBatchDetailsByTask(@RequestParam Long userId, @RequestParam(defaultValue = "0") Integer task) {
        if (task.intValue() >= (Task.values()).length)
            task = Integer.valueOf(0);
        return this.batchService.getBatchDetailsByTask(userId, Task.values()[task.intValue()]);
    }

    @GetMapping({"batch-details/completed-sentences"})
    public ResponseEntity getCompletedSentencesPerBatchDetails(Long batchDetailsId) {
        return this.batchService.getCompletedSentencesPerBatchDetails(batchDetailsId);
    }

    @GetMapping({"batch-details/stats"})
    public ResponseEntity<List<BatchInfoStatsDto>> getCompletedSentencesPerBatchDetails() {
        return this.batchService.getBatchStats();
    }

    @GetMapping("/voice/download")
    public ResponseEntity downloadVoiceFiles(@RequestParam Long batchDetailsId) {
        return batchService.getCompletedSentencesPerBatchDetails(batchDetailsId);
    }

    @GetMapping({"batch-details/expert-reviewed"})
    public ResponseEntity getSentencesAfterExpertReview(Long batchDetailsId) {
        return this.batchService.getTranslatedSentences(batchDetailsId);
    }

    @GetMapping({"expert-reviewed-sentences"})
    public ResponseEntity getExpertReviewedSentences(Long languageId) {
        return this.batchService.getExpertReviewedSentences(languageId);
    }

    @GetMapping({"audio-batches/populate"})
    public ResponseEntity<ResponseMessage> populateAudioBatchesFromS3(String name, Long languageId) {
        return this.amazonClient.populateAudioBatchesFromS3(name, languageId);
    }
}
