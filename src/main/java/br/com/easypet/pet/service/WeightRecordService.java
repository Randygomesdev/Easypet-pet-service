package br.com.easypet.pet.service;

import br.com.easypet.pet.domain.entity.Pet;
import br.com.easypet.pet.domain.entity.WeightRecord;
import br.com.easypet.pet.domain.model.HistorySource;
import br.com.easypet.pet.dto.request.WeightRecordRequest;
import br.com.easypet.pet.dto.response.WeightRecordResponse;
import br.com.easypet.pet.exception.ResourceNotFoundException;
import br.com.easypet.pet.mapper.WeightRecordMapper;
import br.com.easypet.pet.repository.PetRepository;
import br.com.easypet.pet.repository.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class WeightRecordService {

    private final WeightRecordRepository weightRecordRepository;
    private final PetRepository petRepository;
    private final WeightRecordMapper weightRecordMapper;

    public WeightRecordResponse create(UUID petId, WeightRecordRequest request) {
        log.info("Registrando nova pesagem para o pet ID: {}", petId);
        Pet pet = findPetIfOwner(petId);
        WeightRecord record = weightRecordMapper.toEntity(request, pet, resolveSource());

        pet.setWeight(request.weight());
        petRepository.save(pet);

        return weightRecordMapper.toResponse(weightRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public Page<WeightRecordResponse> findAllByPet(UUID petId, Pageable pageable) {
        findPetIfOwner(petId);
        return weightRecordRepository.findAllByPetIdOrderByDateDesc(petId, pageable)
                .map(weightRecordMapper::toResponse);
    }

    public void delete(UUID petId, UUID recordId) {
        findPetIfOwner(petId);
        weightRecordRepository.deleteById(recordId);
    }

    @Transactional(readOnly = true)
    private Pet findPetIfOwner(UUID petId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isPartnerOrAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PARTNER"));
        return petRepository.findById(petId)
                .filter(p -> isPartnerOrAdmin || p.getOwnerId().equals(UUID.fromString(auth.getCredentials().toString())))
                .orElseThrow(() -> new ResourceNotFoundException("Pet não encontrado ou acesso negado"));
    }

    private HistorySource resolveSource() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return HistorySource.OWNER;
        boolean isPartner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PARTNER") || a.getAuthority().equals("ROLE_ADMIN"));
        return isPartner ? HistorySource.PLATFORM : HistorySource.OWNER;
    }
}
