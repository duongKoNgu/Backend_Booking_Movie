package com.movie.service.implement;

import com.movie.dto.OrderDtos.*;
import com.movie.entity.*;
import com.movie.repository.*;
import com.movie.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceIml implements OrderService {
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final ShowtimeRepository showtimeRepository;

    @Override
    @Transactional
    public String createOrder(OrderCreateRequest request) {
        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new RuntimeException("Lịch chiếu không tồn tại"));

        // Kiểm tra ghế trùng (Lưu ý: Bạn nên cấu hình ticketRepository chỉ lấy vé của đơn PAID hoặc PENDING, không lấy CANCELLED)
        List<Ticket> soldTickets = ticketRepository.findByShowtimeId(showtime.getId());
        List<String> soldSeatNumbers = soldTickets.stream()
                .map(Ticket::getSeatNumber)
                .toList();

        for (String requestedSeat : request.getSeatNumbers()) {
            if (soldSeatNumbers.contains(requestedSeat)) {
                throw new RuntimeException("Ghế " + requestedSeat + " đã có người đặt hoặc đang được giữ!");
            }
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setShowtime(showtime);

        // 1. CHỈ GIỮ GHẾ -> TRẠNG THÁI LÀ PENDING
        order.setStatus("PENDING");

        String uniqueOrderCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setOrderCode(uniqueOrderCode);

        double totalAmount = showtime.getBasePrice() * request.getSeatNumbers().size();
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        List<Ticket> newTickets = request.getSeatNumbers().stream().map(seatNum -> {
            Ticket ticket = new Ticket();
            ticket.setOrder(savedOrder);
            ticket.setShowtime(showtime);
            ticket.setSeatNumber(seatNum);
            ticket.setPrice(showtime.getBasePrice());
            return ticket;
        }).collect(Collectors.toList());

        ticketRepository.saveAll(newTickets);

        return savedOrder.getOrderCode();
    }

    // 2. HÀM MỚI: XÁC NHẬN THANH TOÁN THÀNH CÔNG
    @Override
    @Transactional
    public void confirmPayment(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PAID");
            orderRepository.save(order);
        } else if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Đơn hàng này đã quá thời gian thanh toán và bị hủy!");
        }
    }

    // 3. JOB CHẠY NGẦM: TỰ ĐỘNG HỦY ĐƠN QUÁ 10 PHÚT
    // Chạy mỗi 1 phút (60000 ms) một lần
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoCancelExpiredOrders() {
        // Lấy mốc thời gian 10 phút trước
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);

        // Tìm các đơn PENDING tạo trước mốc 10 phút
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore("PENDING", tenMinutesAgo);

        for (Order order : expiredOrders) {
            order.setStatus("CANCELLED");

            // QUAN TRỌNG: Xóa các vé (Ticket) đã tạo để giải phóng ghế cho người khác mua
            ticketRepository.deleteAll(order.getTickets());
            order.getTickets().clear();
        }

        if (!expiredOrders.isEmpty()) {
            orderRepository.saveAll(expiredOrders);
            System.out.println("[SYSTEM] Đã tự động hủy " + expiredOrders.size() + " đơn hàng hết hạn giữ chỗ.");
        }
    }

    @Override
    public void cancelOrder(Long id) {
        Order order = getById(id);
        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }

    @Override
    public Page<Order> getByUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    @Override
    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng id: " + id));
    }

    @Override
    public List<String> getBookedSeats(Long showtimeId) {
        return orderRepository.findBookedSeatsByShowtimeId(showtimeId);
    }
}
