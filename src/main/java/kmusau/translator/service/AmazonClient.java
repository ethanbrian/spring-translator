package kmusau.translator.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import kmusau.translator.DTOs.translatedSentencesDTOs.TranslatedSentenceItemDto;
import kmusau.translator.DTOs.translatedSentencesDTOs.VoicesToReviewDto;
import kmusau.translator.entity.*;
import kmusau.translator.enums.BatchStatus;
import kmusau.translator.enums.BatchType;
import kmusau.translator.enums.StatusTypes;
import kmusau.translator.repository.*;
import kmusau.translator.response.ResponseMessage;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AmazonClient {

    @Autowired
    Logger logger;

    @Autowired
    VoiceRepository voiceRepo;

    @Autowired
    TranslatedSentenceRepository translatedRepo;

    @Autowired
    BatchDetailsRepository batchDetailsRepository;

    @Autowired
    BatchRepository batchRepository;

    @Autowired
    BatchDetailsStatsRepository batchDetailsStatsRepository;

    @Autowired
    SentenceRepository sentenceRepository;

    private AmazonS3 s3client;

    @Value("${amazonProperties.endpointUrl}")
    private String endpointUrl;
    @Value("${amazonProperties.bucketName}")
    private String bucketName;
    @Value("${amazonProperties.accessKey}")
    private String accessKey;
    @Value("${amazonProperties.secretKey}")
    private String secretKey;
    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private PopulateAudioIndexRepository populateAudioIndexRepository;

    @PostConstruct
    private void initializeAmazon() {
        BasicAWSCredentials creds = new BasicAWSCredentials(this.accessKey, this.secretKey);

        this.s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withRegion(Regions.EU_CENTRAL_1)
                .build();


//        AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
//        this.s3client = new AmazonS3Client(credentials, Region.getRegion(Regions.EU_CENTRAL_1));
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = new File(file.getName());
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }

    private String generateFileName(MultipartFile multiPart) {
        return new Date().getTime() + "-" + multiPart.getName().replace(" ", "_");
    }

    private void uploadFileTos3bucket(String fileName, InputStream inputStream) {
        s3client.putObject(bucketName, fileName, inputStream,null);
    }


    public ResponseEntity<ResponseMessage> uploadFile(MultipartFile multipartFile, Long translatedSentenceId, Long voiceId) throws Exception {
        TranslatedSentenceEntity translatedSentenceEntity = translatedRepo.findById(translatedSentenceId).get();
        VoiceEntity voice = new VoiceEntity();
        String fileUrl;

        String fileName = new Date().getTime() + "_" + multipartFile.getOriginalFilename();
            //fileName = generateFileName(multipartFile);
        String storeFileUrl = endpointUrl + "/" + bucketName + "/" + fileName;
            uploadFileTos3bucket(fileName, multipartFile.getInputStream());
            //file.delete();


        fileUrl = generatePresignedUrl(fileName);
        translatedSentenceEntity.setRecordedStatus(StatusTypes.recorded);
        voice.setTranslatedSentenceId(translatedSentenceId);
        voice.setFileUrl(storeFileUrl);
        voice.setDateCreated(new Date());
        voice.setDateModified(new Date());
        voice.setStatus(StatusTypes.unreviewed);
        voice.setPresignedUrl("");
        if (voiceId != null){
            voice.setVoiceId(voiceId);
        }
        voiceRepo.save(voice);

        Optional<BatchDetailsEntity> optionalBatchDetails = batchDetailsRepository.findById(translatedSentenceEntity.getBatchDetailsId());
        if (optionalBatchDetails.isPresent()){
            BatchDetailsEntity batchDetails = optionalBatchDetails.get();
            if (batchDetails.getBatchStatus() == BatchStatus.assignedRecorder){ //Update user stats
                Optional<BatchDetailsStatsEntity> optionalUserStats = batchDetailsStatsRepository.findByBatchDetailsBatchDetailsId(batchDetails.getBatchDetailsId());
                if (optionalUserStats.isPresent()){
                    BatchDetailsStatsEntity userStats = optionalUserStats.get();
                    int audiosRecorded = userStats.getAudiosRecorded() + 1;
                    userStats.setAudiosRecorded(audiosRecorded);
                    batchDetailsStatsRepository.save(userStats);
                }
            }
        }

        return ResponseEntity.ok(new ResponseMessage(fileUrl));
    }

    public ResponseEntity<ResponseMessage> updateFile(Long voiceId, MultipartFile multipartFile) throws Exception {
        if (voiceId == null){
            return ResponseEntity.badRequest().body(new ResponseMessage("Please provide voice id"));
        }
        Optional<VoiceEntity> optionalVoice = voiceRepo.findById(voiceId);
        if (optionalVoice.isEmpty()){
            return ResponseEntity.badRequest().body(new ResponseMessage("Voice does not exist"));
        }
        VoiceEntity voiceEntity = optionalVoice.get();
        return uploadFile(multipartFile, voiceEntity.getTranslatedSentenceId(), voiceEntity.getVoiceId());
    }

    public String deleteFileFromS3Bucket(Long id, boolean deleteVoiceFromDb) {
        VoiceEntity voiceEntity = voiceRepo.findById(id).get();
        String fileUrl = voiceEntity.getFileUrl();
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        logger.info(fileName);
        s3client.deleteObject(new DeleteObjectRequest(bucketName, fileName));

        if (deleteVoiceFromDb) {
            voiceRepo.deleteById(id);
        }
        return "Successfully deleted";
    }

    public String getSingleAudio(Long voiceId) {
//        System.setProperty(SDKGlobalConfiguration.ENABLE_S3_SIGV4_SYSTEM_PROPERTY, "true");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, 1); // Generatecd URL will be valid for 24 hours

        VoiceEntity voiceEntity = voiceRepo.findById(voiceId).get();
        URL fileUrl;
        String storedFileUrl = voiceEntity.getFileUrl();
        String fileName = storedFileUrl.substring(storedFileUrl.lastIndexOf("/") + 1);
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileName)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(calendar.getTime());

        fileUrl = this.s3client.generatePresignedUrl(generatePresignedUrlRequest);
        return fileUrl.toString();
//        return fileUrl;
    }

    public String generatePresignedUrl(String fileUrl) {
        if (Strings.isBlank(fileUrl)){
            return null;
        }
        String[] fileUrlSplit = fileUrl.split(bucketName + "/");
        String fileName = fileUrlSplit[fileUrlSplit.length - 1];
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileName)
                        .withMethod(HttpMethod.GET);

        return this.s3client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }

    public ResponseEntity<VoicesToReviewDto> fetchAudioReviewersTasks(Long reviewerId, Long batchDetailsId) {
        List<BatchDetailsEntity> batchesDetailsToVerify;
        if (batchDetailsId != null)
            batchesDetailsToVerify =
                    batchDetailsRepository.findAllByBatchDetailsIdAndAudioVerifiedById(batchDetailsId, reviewerId);
        else
            batchesDetailsToVerify = batchDetailsRepository.findAllByAudioVerifiedById(reviewerId);

        String language = null;
        List<TranslatedSentenceItemDto> unreviewedAudios = new ArrayList<>();
        List<TranslatedSentenceItemDto> reviewedAudios = new ArrayList<>();

        for (BatchDetailsEntity batchDetails: batchesDetailsToVerify){
            language = batchDetails.getLanguage().getName();
            batchDetailsId = batchDetails.getBatchDetailsId();

            unreviewedAudios = voiceRepo.findUnreviewedAudios(reviewerId, batchDetailsId)
                    .stream()
                    .map(entity -> {
                        String presignedUrl = generatePresignedUrl(entity.getFileUrl());
                        entity.setPresignedUrl(presignedUrl);
                        return TranslatedSentenceItemDto.voiceEntityToDto(entity, null, null);
                    })
                    .collect(Collectors.toList());

            reviewedAudios = voiceRepo.findReviewedAudios(reviewerId, batchDetailsId)
                    .stream()
                    .map(entity -> {
                        Boolean isAccepted;
                        if (entity.getStatus() == StatusTypes.unreviewed)
                            isAccepted = null;
                        else {
                            isAccepted = entity.getStatus() == StatusTypes.approved;
                        }
                        String presignedUrl = generatePresignedUrl(entity.getFileUrl());
                        entity.setPresignedUrl(presignedUrl);
                        return TranslatedSentenceItemDto.voiceEntityToDto(entity, null, isAccepted);
                    })
                    .collect(Collectors.toList());
        }

        VoicesToReviewDto voicesToReviewDto = new VoicesToReviewDto();
        voicesToReviewDto.setBatchDetailsId(batchDetailsId);
        voicesToReviewDto.setLanguage(language);
        voicesToReviewDto.setReviewedAudios(reviewedAudios);
        voicesToReviewDto.setUnreviewedAudios(unreviewedAudios);
        return ResponseEntity.ok(voicesToReviewDto);
    }

    @Transactional
    public ResponseEntity<ResponseMessage> populateAudioBatchesFromS3(String name, Long languageId) {
        String batchPrefix = name;
        Optional<LanguageEntity> language = languageRepository.findById(languageId);
        String audioBucketName = "audio_files/" + batchPrefix;

        ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(audioBucketName);

        ListObjectsV2Result listObjectsResult;
        List<S3ObjectSummary> objectSummaries = new ArrayList<>();

        do {
            listObjectsResult = s3client.listObjectsV2(listObjectsRequest);
            objectSummaries.addAll(listObjectsResult.getObjectSummaries());
            String token = listObjectsResult.getNextContinuationToken();
            listObjectsRequest.setContinuationToken(token);
        } while (listObjectsResult.isTruncated());

        objectSummaries.sort((summary, summary1) -> {
            String[] splitAudioLink = summary.getKey().split("/");
            String fileName = splitAudioLink[splitAudioLink.length - 1];
            String[] splitAudioLink1 = summary1.getKey().split("/");
            String fileName1 = splitAudioLink1[splitAudioLink1.length - 1];

            Integer fileIndex = Integer.valueOf(fileName.split(".wav")[0]);
            Integer fileIndex1 = Integer.valueOf(fileName1.split(".wav")[0]);

            return Integer.compare(fileIndex, fileIndex1);
        });

        ArrayList<SentenceEntity> audioSentences = new ArrayList<>();
        Long currentBatchNo = null;

        PopulateAudioIndexEntity populateAudioIndexEntity = populateAudioIndexRepository.getPopulateAudioIndexEntitiesByBucketName(name);
        if (populateAudioIndexEntity == null){
            populateAudioIndexEntity = new PopulateAudioIndexEntity();
            populateAudioIndexEntity.setLastAudioIndex(0);
            populateAudioIndexEntity.setBucketName(name);
        }
        Long index = populateAudioIndexEntity.getLastAudioIndex();

        for (; index < objectSummaries.size(); index++) {
            if (index % 500 == 0){
                String batchName = batchPrefix + " (" + (index + 1) + "-" + (index + 500) + ")";
                BatchEntity batchEntity = new BatchEntity(batchName, "DrLugha/" + batchPrefix, "Extracted from DrLugha " + batchPrefix, BatchType.AUDIO, language.get());
                currentBatchNo = batchRepository.save(batchEntity).getBatchNo();
            }
            S3ObjectSummary objectSummary = objectSummaries.get(index.intValue());
            String fileName = objectSummary.getKey();
            String storeFileUrl = endpointUrl + "/"  + bucketName + "/" +  fileName;
            SentenceEntity audioSentence = new SentenceEntity(currentBatchNo, storeFileUrl);
            audioSentences.add(audioSentence);
        }

        sentenceRepository.saveAll(audioSentences);
        populateAudioIndexEntity.setLastAudioIndex(index);
        populateAudioIndexRepository.save(populateAudioIndexEntity);

        return ResponseEntity.ok(new ResponseMessage("Databases successfully populated"));
    }
}
