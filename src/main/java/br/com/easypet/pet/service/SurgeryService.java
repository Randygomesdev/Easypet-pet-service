package br.com.easypet.pet.service;

import br.com.easypet.pet.domain.entity.Pet;
import br.com.easypet.pet.domain.entity.Surgery;
import br.com.easypet.pet.domain.model.HistorySource;
import br.com.easypet.pet.dto.request.SurgeryRequest;
import br.com.easypet.pet.dto.response.SurgeryResponse;
import br.com.easypet.pet.exception.ResourceNotFoundException;
import br.com.easypet.pet.mapper.SurgeryMapper;
import br.com.easypet.pet.repository.PetRepository;
import br.com.easypet.pet.repository.SurgeryRepository;
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
public class SurgeryService {

    private final SurgeryRepository surgeryRepository;
    private final PetRepository petRepository;
    private final SurgeryMapper surgeryMapper;

    @CacheEvict(value = "surgeries", key = "#p0")
    public SurgeryResponse create(UUID petId, SurgeryRequest request) {
        log.info("Registrando cirurgia '{}' para o pet ID: {}", request.description(), petId);
        Pet pet = findPetIfOwner(petId);
        Surgery surgery = surgeryMapper.toEntity(request, pet, resolveSource());
        return surgeryMapper.toResponse(surgeryRepository.save(surgery));
    }

    @Transactional(readOnly = true)
    public Page<SurgeryResponse> findAllByPet(UUID petId, Pageable pageable) {
        findPetIfOwner(petId);
        return surgeryRepository.findAllByPetIdOrderByDateDesc(petId, pageable)
                .map(surgeryMapper::toResponse);
    }

    @CacheEvict(value = "surgeries", key = "#p0")
    public SurgeryResponse update(UUID petId, UUID surgeryId, SurgeryRequest request) {
        log.info("Atualizando cirurgia ID: {} para o pet ID: {}", surgeryId, petId);
        findPetIfOwner(petId);

        Surgery surgery = surgeryRepository.findById(surgeryId)
                .filter(s -> s.getPet().getId().equals(petId))
                .orElseThrow(() -> new ResourceNotFoundException("Cirurgia não encontrada"));
        updateSurgeryFromRequest(surgery, request);
        return surgeryMapper.toResponse(surgeryRepository.save(surgery));
    }

    @CacheEvict(value = "surgeries", key = "#p0")
    public void delete(UUID petId, UUID surgeryId) {
        findPetIfOwner(petId);
        surgeryRepository.deleteById(surgeryId);
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

    private void updateSurgeryFromRequest(Surgery surgery, SurgeryRequest request) {
        surgery.setDescription(request.description());
        surgery.setDate(request.date());
        surgery.setVetName(request.vetName());
        surgery.setProviderId(request.providerId());
        surgery.setAnesthesiaType(request.anesthesiaType());
        surgery.setPostOperativeInstructions(request.postOperativeInstructions());
        surgery.setStatus(request.status());
    }
}
