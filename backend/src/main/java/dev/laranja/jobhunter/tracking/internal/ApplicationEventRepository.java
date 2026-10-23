package dev.laranja.jobhunter.tracking.internal;

import org.springframework.data.repository.Repository;

/** Só insere: o histórico do funil não é editado nem apagado. */
public interface ApplicationEventRepository extends Repository<ApplicationEvent, Long> {

	ApplicationEvent save(ApplicationEvent event);

}
