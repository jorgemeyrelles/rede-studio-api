package br.com.redestudio.services;

import br.com.redestudio.components.EquipmentFunctions;
import br.com.redestudio.dtos.request.BulkCreateEquipmentsRequest;
import br.com.redestudio.dtos.request.CreateEquipmentRequest;
import br.com.redestudio.dtos.request.EquipmentPriceRequest;
import br.com.redestudio.dtos.request.UpdateEquipmentRequest;
import br.com.redestudio.dtos.response.EquipmentPriceResponse;
import br.com.redestudio.dtos.response.EquipmentResponse;
import br.com.redestudio.entities.EquipmentEntity;
import br.com.redestudio.messaging.EquipmentBulkCreateMessage;
import br.com.redestudio.messaging.EquipmentCreateMessage;
import br.com.redestudio.messaging.EquipmentDeleteMessage;
import br.com.redestudio.messaging.EquipmentMutationProducer;
import br.com.redestudio.messaging.EquipmentUpdateMessage;
import br.com.redestudio.repositories.EquipmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for equipment catalog lifecycle operations.
 *
 * <p>Follows the LB → Redis → fila → BD pattern (see {@code CLAUDE.md}) for
 * the two paths that need read-after-write consistency: {@link #getById}
 * and {@link #listAll} (kept in sync on every write, mirroring
 * {@code ProjectService}'s owner-list handling — the whole catalog here
 * plays the same role a single owner's project list plays there, since
 * equipment has no ownership scoping).
 *
 * <p>{@link #listByBrand} and {@link #searchByModelContains} deliberately
 * read straight from MongoDB (via {@link EquipmentRepository}) instead of
 * the cache: the catalog is admin-managed and changes infrequently, so a
 * short (sub-second) eventual-consistency window on these two filtered
 * views — the time it takes {@code EquipmentMutationConsumer} to catch up
 * — is an acceptable trade-off against caching every possible brand/search
 * key combination.
 */
@ApplicationScoped
public class EquipmentService {

    @Inject
    EquipmentRepository equipmentRepository;

    @Inject
    EquipmentCacheService cacheService;

    @Inject
    EquipmentMutationProducer producer;

    /**
     * Lists every equipment in the catalog — Redis first, Mongo on a miss.
     *
     * @return list of equipment (may be empty)
     */
    public List<EquipmentResponse> listAll() {
        return cacheService.getAll().orElseGet(this::loadAllFromMongo);
    }

    /**
     * Lists every equipment of the given brand — reads straight from Mongo.
     *
     * @param brand the brand to filter by (exact match)
     * @return list of matching equipment (may be empty)
     */
    public List<EquipmentResponse> listByBrand(String brand) {
        return equipmentRepository.listByBrand(brand).stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    /**
     * Finds equipment whose model contains the given substring — reads
     * straight from Mongo.
     *
     * @param term the substring to search for (case-insensitive)
     * @return list of matching equipment (may be empty)
     */
    public List<EquipmentResponse> searchByModelContains(String term) {
        return equipmentRepository.findByModelContains(term).stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    /**
     * Fetches a single equipment by id — Redis first, Mongo on a miss.
     *
     * @param id the equipment id
     * @return the matching equipment
     * @throws NotFoundException if the id is malformed or doesn't exist
     */
    public EquipmentResponse getById(String id) {
        return cacheService.getOne(id).orElseGet(() -> loadOneFromMongo(id));
    }

    /**
     * Creates a new equipment catalog entry. Pre-generates the id so the
     * Redis entry written here and the MongoDB document eventually written
     * by the consumer always agree on the same id.
     *
     * @param request the equipment data
     * @return the created equipment
     */
    public EquipmentResponse create(CreateEquipmentRequest request) {
        ObjectId id = new ObjectId();
        List<String> function = EquipmentFunctions.normalize(request.getFunction());
        EquipmentResponse response = toResponse(id, request.getBrand(), request.getModel(),
                function, request.getPrice());

        cacheService.putOne(response);
        prependToAllCache(response);

        producer.publishCreate(new EquipmentCreateMessage(
                id.toHexString(), request.getBrand(), request.getModel(), function, request.getPrice()));

        return response;
    }

    /**
     * Creates many equipment catalog entries in one call — same
     * id-pre-generation and cache/queue handling as {@link #create}, batched.
     *
     * @param request the equipment entries to create
     * @return the created equipment, in the same order as the request
     */
    public List<EquipmentResponse> bulkCreate(BulkCreateEquipmentsRequest request) {
        List<EquipmentResponse> responses = new ArrayList<>(request.getItems().size());
        List<EquipmentCreateMessage> messages = new ArrayList<>(request.getItems().size());

        for (CreateEquipmentRequest item : request.getItems()) {
            ObjectId id = new ObjectId();
            List<String> function = EquipmentFunctions.normalize(item.getFunction());
            EquipmentResponse response = toResponse(id, item.getBrand(), item.getModel(),
                    function, item.getPrice());
            responses.add(response);
            messages.add(new EquipmentCreateMessage(
                    id.toHexString(), item.getBrand(), item.getModel(), function, item.getPrice()));
        }

        responses.forEach(cacheService::putOne);
        prependAllToAllCache(responses);

        producer.publishBulkCreate(new EquipmentBulkCreateMessage(messages));

        return responses;
    }

    /**
     * Partially updates an equipment catalog entry — only non-null request
     * fields are applied.
     *
     * @param id      the equipment id
     * @param request the fields to change (all optional)
     * @return the updated equipment
     * @throws NotFoundException if the id is malformed or doesn't exist
     */
    public EquipmentResponse update(String id, UpdateEquipmentRequest request) {
        EquipmentResponse current = getById(id);

        String brand = request.getBrand() != null ? request.getBrand() : current.getBrand();
        String model = request.getModel() != null ? request.getModel() : current.getModel();
        List<String> function = request.getFunction() != null
                ? EquipmentFunctions.normalize(request.getFunction())
                : current.getFunction();
        EquipmentPriceRequest price = request.getPrice() != null
                ? request.getPrice()
                : new EquipmentPriceRequest(
                        current.getPrice().getApproxPriceUsd(),
                        current.getPrice().getApproxPriceBrl(),
                        current.getPrice().getScannedAt());

        EquipmentResponse updated = new EquipmentResponse(
                current.getId(), brand, model, function, EquipmentPriceResponse.from(toEntityPrice(price)),
                current.getCreatedAt(), Instant.now());

        cacheService.putOne(updated);
        replaceInAllCache(updated);

        producer.publishUpdate(new EquipmentUpdateMessage(id, brand, model, function, price));
        return updated;
    }

    /**
     * Deletes an equipment catalog entry.
     *
     * @param id the equipment id
     * @throws NotFoundException if the id is malformed or doesn't exist
     */
    public void delete(String id) {
        getById(id); // valida existência antes de qualquer efeito

        cacheService.evictOne(id);
        List<EquipmentResponse> updatedList = new ArrayList<>(listAll());
        updatedList.removeIf(e -> e.getId().equals(id));
        cacheService.putAll(updatedList);

        producer.publishDelete(new EquipmentDeleteMessage(id));
    }

    private void prependToAllCache(EquipmentResponse item) {
        List<EquipmentResponse> updatedList = new ArrayList<>(listAll());
        updatedList.add(0, item);
        cacheService.putAll(updatedList);
    }

    private void prependAllToAllCache(List<EquipmentResponse> items) {
        List<EquipmentResponse> updatedList = new ArrayList<>(listAll());
        updatedList.addAll(0, items);
        cacheService.putAll(updatedList);
    }

    private void replaceInAllCache(EquipmentResponse item) {
        List<EquipmentResponse> updatedList = new ArrayList<>(listAll());
        updatedList.removeIf(e -> e.getId().equals(item.getId()));
        updatedList.add(0, item);
        cacheService.putAll(updatedList);
    }

    private List<EquipmentResponse> loadAllFromMongo() {
        List<EquipmentResponse> items = equipmentRepository.listAll().stream()
                .map(EquipmentResponse::from)
                .toList();
        cacheService.putAll(items);
        return items;
    }

    private EquipmentResponse loadOneFromMongo(String id) {
        if (!ObjectId.isValid(id)) {
            throw new NotFoundException("Equipment not found: " + id);
        }
        EquipmentEntity equipment = equipmentRepository.findByIdOptional(new ObjectId(id))
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + id));
        EquipmentResponse response = EquipmentResponse.from(equipment);
        cacheService.putOne(response);
        return response;
    }

    private static EquipmentResponse toResponse(
            ObjectId id, String brand, String model, List<String> function, EquipmentPriceRequest price) {
        Instant now = Instant.now();
        return new EquipmentResponse(
                id.toHexString(), brand, model, function,
                EquipmentPriceResponse.from(toEntityPrice(price)), now, now);
    }

    private static br.com.redestudio.entities.EquipmentPrice toEntityPrice(EquipmentPriceRequest price) {
        return new br.com.redestudio.entities.EquipmentPrice(
                price.getApproxPriceUsd(), price.getApproxPriceBrl(), price.getScannedAt());
    }
}
