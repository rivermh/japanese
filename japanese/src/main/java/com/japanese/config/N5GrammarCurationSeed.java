package com.japanese.config;

import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.entity.GrammarRelationType;
import com.japanese.content.service.GrammarCurationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A deliberately opt-in review queue for human-authored N5 curation.
 *
 * <p>It never changes imported Grammar rows and it never approves or publishes an item. Every
 * record carries a public reference and remains PENDING until a reviewer makes an individual
 * approval decision. Re-running the profile is safe for enrichments, relations, comparisons, and
 * question source references.</p>
 */
@Component
@Profile("grammar-curation")
public class N5GrammarCurationSeed implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(N5GrammarCurationSeed.class);
    private static final String SOURCE = "https://jlptsensei.com/jlpt-n5-grammar-list/";
    private static final String SOURCE_TAE_KIM = "https://www.guidetojapanese.org/grammar_guide.pdf";

    private final GrammarCurationService curation;

    public N5GrammarCurationSeed(GrammarCurationService curation) {
        this.curation = curation;
    }

    @Override
    public void run(String... args) {
        DRAFTS.forEach(draft -> curation.saveEnrichment(slug(draft.noteId()), draft.sourceRef(), draft.nuance(),
                draft.usage(), draft.formation(), draft.mistake(), draft.note()));
        RELATIONS.forEach(relation -> {
            var saved = curation.saveRelation(slug(relation.firstNoteId()), slug(relation.secondNoteId()),
                    relation.type(), relation.sourceRef());
            curation.saveComparison(saved.getId(), relation.sourceRef(), relation.summary(), relation.keyDifference(),
                    relation.usageDifference(), relation.commonConfusion());
        });
        QUESTIONS.forEach(question -> curation.saveQuestion(slug(question.noteId()), GrammarConfirmationType.CONTEXT_GAP,
                question.prompt(), question.context(), question.explanation(), question.sourceRef(), List.of(
                        new GrammarCurationService.ChoiceDraft(question.correct(), true),
                        new GrammarCurationService.ChoiceDraft(question.wrongOne(), false),
                        new GrammarCurationService.ChoiceDraft(question.wrongTwo(), false),
                        new GrammarCurationService.ChoiceDraft(question.wrongThree(), false))));
        log.info("Seeded {} pending N5 enrichments, {} pending comparisons/relations, and {} pending confirmation questions. No imported content was published.",
                DRAFTS.size(), RELATIONS.size(), QUESTIONS.size());
    }

    private static String slug(long noteId) { return "jlpt-max-grammar-" + noteId; }

    private record Draft(long noteId, String nuance, String usage, String formation, String mistake, String note,
                         String sourceRef) { }
    private record RelationDraft(long firstNoteId, long secondNoteId, GrammarRelationType type, String summary,
                                 String keyDifference, String usageDifference, String commonConfusion,
                                 String sourceRef) { }
    private record QuestionDraft(long noteId, String prompt, String context, String correct, String wrongOne,
                                 String wrongTwo, String wrongThree, String explanation, String sourceRef) { }

    private static Draft draft(long noteId, String nuance, String usage, String formation, String mistake, String note) {
        return new Draft(noteId, nuance, usage, formation, mistake, note, SOURCE);
    }
    private static QuestionDraft question(long noteId, String prompt, String context, String correct, String wrongOne,
                                          String wrongTwo, String wrongThree, String explanation) {
        return new QuestionDraft(noteId, prompt, context, correct, wrongOne, wrongTwo, wrongThree, explanation, SOURCE_TAE_KIM);
    }

    private static final List<Draft> DRAFTS = List.of(
            draft(1788408430326L, "화자와 듣는 사람 모두에게서 멀리 떨어진 명사를 가리키는 관형 표현입니다.", "눈에 보이는 대상이나 이미 대화에서 특정된 대상을 뒤의 명사와 함께 가리킬 때 씁니다.", "あの + 명사", "명사 없이 단독으로 쓰지 않습니다. 단독으로 가리킬 때는 あれ를 씁니다.", "この・その・あの는 대상과 화자/청자 사이의 거리 관계를 함께 기억하세요."),
            draft(1788408430328L, "부정형과 함께 쓰여 '그다지 ~하지 않다'라는 낮은 정도를 나타냅니다.", "습관이나 빈도가 많지 않음을 말할 때 자연스럽습니다.", "あまり + 동사 부정형", "긍정문에 그대로 붙여 '매우'의 뜻으로 쓰지 않습니다.", "あまり는 뒤의 부정 표현과 한 덩어리로 익히세요."),
            draft(1788408430344L, "둘 이상의 가능성 중 어느 쪽인지 모를 때 간접 의문을 만듭니다.", "알고 있는지, 결정했는지, 확인하는지처럼 선택 여부를 말할 때 씁니다.", "보통형 + かどうか", "의문사(何・どこ 등)가 이미 있으면 보통 かどうか를 겹쳐 쓰지 않습니다.", "선택 여부라면 かどうか, 구체적 정보라면 의문사를 먼저 떠올리세요."),
            draft(1788408430346L, "이유·원인을 비교적 직접적으로 말하는 연결 표현입니다.", "일상적인 이유 설명과 권유·요청의 근거를 말할 때 자주 씁니다.", "보통형 + から / 명사·な형용사 + だから", "문장의 원인과 결과를 뒤바꾸지 않도록 결과를 뒤 절에 둡니다.", "이유를 먼저 말한 뒤 자연스러운 결과가 오는지 확인하세요."),
            draft(1788408430364L, "좋아함·선호를 나타내며, 좋아하는 대상에는 が가 흔히 쓰입니다.", "음식, 활동, 사람에 대한 선호를 말할 때 사용합니다.", "명사 + が 好きです", "好き를 일반 타동사처럼 목적어에 を만 붙여 쓰지 않습니다.", "好き는 형용동사 성격의 표현이라 が와 함께 익히는 편이 안전합니다."),
            draft(1788408430376L, "능력 또는 가능한 일을 명시적으로 나타내는 표현입니다.", "어떤 행동을 할 수 있는지, 기술·상황상 가능함을 말할 때 씁니다.", "동사 사전형 + ことができます", "희망을 나타내는 ～たい와 바꾸어 쓰지 않습니다.", "가능은 ことができる, 하고 싶음은 ～たい로 나누어 기억하세요."),
            draft(1788408430380L, "'오직 ~뿐'이라는 제한을 말하며, 뒤에 부정형이 오는 것이 핵심입니다.", "수량이나 선택지가 적음을 강조할 때 씁니다.", "명사/수량 + しか + 부정형", "긍정형과 결합해 '뿐'의 뜻으로 쓰지 않습니다.", "しか를 보면 뒤의 부정형까지 함께 확인하세요."),
            draft(1788408430385L, "두 행동의 순서를 'A를 한 후에 B'로 말합니다.", "첫 행동이 완료된 다음의 행동을 말할 때 씁니다.", "동사 た형 + あとで", "동사 사전형을 그대로 붙이지 않습니다.", "완료한 뒤의 순서라면 た형인지 먼저 확인하세요."),
            draft(1788408430387L, "화자 자신의 하고 싶은 마음이나 희망을 부드럽게 말합니다.", "자신의 희망을 말하거나 상대에게 희망을 물을 때 사용합니다.", "동사 ます형 어간 + たいです", "제3자의 확정된 마음을 단정해 ～たいです로 말하지 않습니다.", "희망(～たい)과 계획·의도(～つもり)를 구분하세요."),
            draft(1788408430395L, "범위를 '오직 이것만'으로 한정하는 중립적인 표현입니다.", "선택·수량·범위를 제한할 때 씁니다.", "명사/수량 + だけ", "しか처럼 반드시 부정형을 요구하지는 않습니다.", "だけ는 긍정·부정 모두 가능하지만, しか는 부정형과 짝입니다."),
            draft(1788408430400L, "화자의 현재 의도나 계획을 나타냅니다.", "이미 생각해 둔 계획을 말할 때 사용합니다.", "동사 사전형/ない형 + つもりです", "막연한 희망을 말하는 ～たいです와 같은 뜻으로 처리하지 않습니다.", "하고 싶은 마음보다 '그렇게 할 생각'에 초점이 있을 때 선택하세요."),
            draft(1788408430406L, "진행 중인 행동이나 지속되는 상태를 나타냅니다.", "지금 하고 있는 일, 반복 습관, 결과 상태를 문맥에 따라 말할 때 사용합니다.", "동사 て형 + います", "모든 동사가 같은 방식으로 진행을 뜻한다고 단정하지 말고 문맥을 확인합니다.", "처음에는 '지금 하고 있다'를 중심으로 익히고, 상태 용법은 예문으로 넓히세요."),
            draft(1788408430410L, "행동의 순서를 'A하고 나서 B'로 연결합니다.", "두 행동을 차례로 말할 때 쓰며, 뒤 행동이 중심 정보인 경우가 많습니다.", "동사 て형 + から", "이유의 から와 형태가 같아도 앞에 て형이 오면 순서 용법입니다.", "앞 동사가 て형인지, 보통형인지로 두 から를 구별하세요."),
            draft(1788408430412L, "상대에게 어떤 행동을 해 달라고 요청하는 기본 표현입니다.", "지시·안내·부탁을 비교적 직접적으로 할 때 사용합니다.", "동사 て형 + ください", "행동을 하지 말라는 요청에는 이 표현을 그대로 쓰지 않습니다.", "금지 요청은 ～ないでください로 바꾸세요."),
            draft(1788408430421L, "행동이 허용되지 않음을 나타내는 금지 표현입니다.", "규칙, 안전 안내, 명확한 금지를 말할 때 씁니다.", "동사 て형 + はいけません", "허가 표현 ～てもいいですか와 뜻이 반대이므로 혼동하지 않습니다.", "문장에 금지 표지나 규칙이 있으면 ～てはいけません을 먼저 검토하세요."),
            draft(1788408430425L, "조건이 성립해도 뒤의 결과가 변하지 않는 양보를 나타냅니다.", "'비가 와도 간다'처럼 예상과 다른 결과를 말할 때 씁니다.", "동사 て형/い형용사 くて + も / 명사·な형용사 + でも", "단순한 허가 질문 ～てもいいですか와 같은 뜻으로 보지 않습니다.", "～ても는 '그래도'의 의미가 자연스러운지 확인하세요."),
            draft(1788408430427L, "상대에게 허가를 정중하게 묻는 표현입니다.", "앉아도 되는지, 사용해도 되는지처럼 허가가 필요한 상황에 사용합니다.", "동사 て형 + もいいですか", "상대에게 명령하거나 자신이 하겠다는 뜻으로 쓰지 않습니다.", "허가를 묻는 상황인지, 금지를 말하는 상황인지 먼저 판단하세요."),
            draft(1788408430441L, "확실하지 않은 추측이나 확인을 부드럽게 나타냅니다.", "상황을 보고 추측하거나 상대의 동의를 구할 때 사용합니다.", "보통형 + でしょう", "확정된 사실을 강하게 단정하는 표현으로 오해하지 않습니다.", "문맥의 단서에서 그럴 법하다고 판단할 때 잘 어울립니다."),
            draft(1788408430453L, "한 행동을 하지 않고 다른 행동을 한 순서·방법을 나타냅니다.", "아침을 먹지 않고 나갔다처럼 '하지 않은 채'를 말할 때 씁니다.", "동사 ない형에서 い를 빼고 + で", "원인·이유를 나타내는 ～なくて와 역할을 섞지 않습니다.", "뒤 행동을 하기 전에 일부러 하지 않은 일이면 ～ないで를 확인하세요."),
            draft(1788408430455L, "상대에게 어떤 행동을 하지 말아 달라고 요청합니다.", "안전 안내나 부드러운 금지 요청에 사용합니다.", "동사 ない형에서 い를 빼고 + でください", "금지의 이유를 말하는 ～なくて와 바꾸어 쓰지 않습니다.", "'하지 마세요'는 한 덩어리로 ～ないでください를 익히세요."),
            draft(1788408430457L, "두 행동이 같은 주체에게 동시에 일어남을 나타냅니다.", "음악을 들으면서 숙제하다처럼 한 사람이 병행하는 행동에 씁니다.", "동사 ます형 어간 + ながら", "앞뒤 행동의 주체가 다를 때는 쓰지 않습니다.", "앞 절은 보조 행동, 뒤 절은 중심 행동으로 읽는 습관이 도움이 됩니다."),
            draft(1788408430459L, "부정 상태나 이유를 이어 뒤의 결과를 말합니다.", "시간이 없어서 못 갔다처럼 원인·상태를 설명할 때 사용합니다.", "동사 ない형에서 い를 빼고 + くて", "'하지 않고 다른 행동'을 뜻하는 ～ないで와 바꾸지 않습니다.", "원인이라면 ～なくて, 의도적으로 생략한 행동이라면 ～ないで를 비교하세요."),
            draft(1788408430471L, "이동의 목적을 나타내는 に입니다.", "무엇을 하러 가는지 말할 때 사용합니다.", "동사 ます형 어간/활동 명사 + に 行きます", "장소를 나타내는 に와 목적을 나타내는 に를 혼동하지 않습니다.", "'무엇을 하러'라고 바꿔 말할 수 있으면 목적의 に일 가능성이 큽니다."),
            draft(1788408430475L, "화자가 여러 선택지 중 하나를 의도적으로 정하는 변화를 나타냅니다.", "색, 메뉴, 방 등을 선택할 때 사용합니다.", "명사 + にします", "저절로 변하는 ～になります와 구분합니다.", "내가 선택하면 にします, 상황이 변하면 になります로 기억하세요."),
            draft(1788408430478L, "상태가 자연스럽게 변하거나 결과적으로 그렇게 됨을 나타냅니다.", "계절·직업·상태 변화처럼 결과 상태를 말할 때 사용합니다.", "명사/な형용사 어간 + に なります", "의도적으로 선택하는 ～にします와 바꾸어 쓰지 않습니다.", "누가 선택했는지보다 변화 결과에 초점이 있으면 になります가 자연스럽습니다."),
            draft(1788408430485L, "이동 목적을 나타내며, 화자 또는 기준점 쪽으로 오는 동작과 함께 씁니다.", "누군가가 여기로 무엇을 하러 오는 상황에 사용합니다.", "동사 ます형 어간/활동 명사 + に 来ます", "목적지에서 멀어지는 이동을 말할 때 行きます와 혼동하지 않습니다.", "오는 방향인지 가는 방향인지 먼저 정하면 선택이 쉬워집니다."),
            draft(1788408430487L, "이동 목적을 나타내며, 기준점에서 다른 곳으로 가는 동작과 함께 씁니다.", "무엇을 하러 가는 계획·행동을 말할 때 사용합니다.", "동사 ます형 어간/활동 명사 + に 行きます", "방향이 화자 쪽으로 오는 경우에는 来ます를 씁니다.", "행동 목적과 이동 방향을 각각 확인하세요."),
            draft(1788408430505L, "둘 이상을 비교해 어느 쪽이 더 그러한지 말합니다.", "선택·선호·특징을 비교할 때 사용합니다.", "비교 대상 + のほうが + 형용사/좋아하다", "비교 기준 없이 단독으로 확정적 최상급처럼 쓰지 않습니다.", "두 대상을 먼저 제시하고 어느 쪽이 더한지 말하는 흐름을 익히세요."),
            draft(1788408430507L, "기준 시점이나 행동보다 앞선 때를 나타냅니다.", "출발 전에 준비하다처럼 순서를 말할 때 사용합니다.", "동사 사전형 + 前に / 명사 + の前に", "동사 과거형을 붙여 '후에' 뜻으로 쓰지 않습니다.", "앞 행동이 아직 일어나기 전인지 확인하세요."),
            draft(1788408430511L, "화자가 상대와 함께 하자고 제안합니다.", "함께 할 행동을 제안하거나 결정을 이끌 때 사용합니다.", "동사 ます형 어간 + ましょう", "상대에게 해 달라고 요청하는 ～てください와 역할이 다릅니다.", "'같이 하자'이면 ましょう, '해 주세요'이면 てください를 선택하세요.")
    );

    private static final List<RelationDraft> RELATIONS = List.of(
            relation(1788408430380L, 1788408430395L, GrammarRelationType.CONFUSABLE, "しか와 だけ는 모두 범위를 제한하지만, 문장의 극성이 다릅니다.", "しか는 부정형과 결합하고, だけ는 긍정·부정 모두에서 쓸 수 있습니다.", "부족함이나 제한을 강하게 말하면 しか, 단순 범위 한정이면 だけ가 자연스럽습니다.", "しか 뒤에 긍정형을 쓰는 오류를 조심하세요."),
            relation(1788408430385L, 1788408430410L, GrammarRelationType.SIMILAR, "둘 다 A 뒤에 B가 이어지는 순서를 나타냅니다.", "～たあとで는 완료 뒤의 시점을, ～てから는 동작 연결의 순서를 강조합니다.", "둘 다 가능할 수 있지만, 초급에서는 형태와 예문을 통째로 익히는 것이 안전합니다.", "～たあとで 앞에 사전형을 붙이지 않도록 주의하세요."),
            relation(1788408430387L, 1788408430400L, GrammarRelationType.CONFUSABLE, "～たい는 희망, ～つもり는 현재의 의도·계획을 나타냅니다.", "원하는 마음은 ～たい, 하기로 생각한 계획은 ～つもり입니다.", "계획을 묻는 상황에는 ～つもり가 더 자연스러울 수 있습니다.", "두 표현을 모두 단순 미래형으로 번역해 같은 뜻으로 처리하지 마세요."),
            relation(1788408430412L, 1788408430455L, GrammarRelationType.CONTRAST, "～てください는 행동을 요청하고, ～ないでください는 행동하지 않기를 요청합니다.", "긍정 요청과 부정 요청의 대립입니다.", "안내문과 부탁에서 모두 자주 쓰입니다.", "동사 활용을 바꾼 뒤 ください만 붙이는 방식으로 기억하세요."),
            relation(1788408430412L, 1788408430511L, GrammarRelationType.CONFUSABLE, "～てください는 상대에게 요청하고, ～ましょう는 함께 하자는 제안입니다.", "요청의 대상은 상대, 제안의 참여자는 화자와 상대입니다.", "상대의 행동만 필요하면 ください, 같이 하자는 뜻이면 ましょう를 씁니다.", "화자 자신이 포함되는지 확인하세요."),
            relation(1788408430421L, 1788408430427L, GrammarRelationType.CONTRAST, "허가를 묻는 ～てもいいですか와 금지를 말하는 ～てはいけません은 반대 방향의 표현입니다.", "전자는 가능한지 묻고, 후자는 해서는 안 됨을 말합니다.", "규칙·안전·장소 안내에서 함께 자주 나타납니다.", "질문문과 금지문을 같은 뜻으로 읽지 않도록 문장 끝을 확인하세요."),
            relation(1788408430453L, 1788408430459L, GrammarRelationType.CONFUSABLE, "～ないで는 하지 않은 채 다음 행동을 말하고, ～なくて는 부정 상태·이유를 잇습니다.", "행동의 생략이면 ～ないで, 원인·상태면 ～なくて입니다.", "뒤 절이 결과인지 다음 행동인지로 판단하면 도움이 됩니다.", "두 표현을 모두 '않아서'로만 번역하지 마세요."),
            relation(1788408430457L, 1788408430410L, GrammarRelationType.CONTRAST, "～ながら는 동시에, ～てから는 순서를 나타냅니다.", "한 사람이 두 행동을 병행하면 ながら, 먼저 한 뒤 다음 행동이면 てから입니다.", "시간 관계를 분명하게 말할 때 구분이 중요합니다.", "ながら에서 두 행동의 주체가 같은지 확인하세요."),
            relation(1788408430475L, 1788408430478L, GrammarRelationType.CONTRAST, "～にします는 선택, ～になります는 변화 결과에 초점이 있습니다.", "의도적 결정과 자연스러운 변화의 차이입니다.", "메뉴 선택과 계절 변화처럼 문맥이 다릅니다.", "변화를 말하면서 무조건 にします를 쓰지 마세요."),
            relation(1788408430485L, 1788408430487L, GrammarRelationType.CONTRAST, "～に来ます와 ～に行きます는 목적은 같지만 이동 방향이 다릅니다.", "기준점 쪽으로 오면 来ます, 기준점에서 떠나면 行きます입니다.", "화자·청자·현재 장소를 기준으로 방향을 판단합니다.", "목적의 に만 보고 来る/行く를 임의로 고르지 마세요."),
            relation(1788408430385L, 1788408430507L, GrammarRelationType.CONTRAST, "～たあとで는 이후, ～前に는 이전의 순서를 나타냅니다.", "한쪽은 완료 뒤, 다른 한쪽은 아직 일어나기 전입니다.", "일과 계획의 시간 순서를 말할 때 함께 비교할 수 있습니다.", "前に 앞의 동사는 사전형이라는 점을 확인하세요."),
            relation(1788408430346L, 1788408430459L, GrammarRelationType.SIMILAR, "둘 다 이유를 말할 수 있지만 형태와 쓰임이 다릅니다.", "から는 절 전체의 이유를 직접 연결하고, なくて는 부정 상태를 원인으로 잇습니다.", "문장의 앞부분이 일반 이유인지 부정 상태인지 구분합니다.", "부정형 뒤에 から와 なくて를 무조건 바꾸어 쓰지 마세요.")
    );

    private static RelationDraft relation(long first, long second, GrammarRelationType type, String summary, String keyDifference,
                                          String usageDifference, String commonConfusion) {
        return new RelationDraft(first, second, type, summary, keyDifference, usageDifference, commonConfusion, SOURCE);
    }

    private static final List<QuestionDraft> QUESTIONS = List.of(
            question(1788408430326L, "빈칸에 가장 알맞은 표현을 고르세요.", "駅の向こうに見える建物は、＿＿＿学校です。", "あの", "この", "その", "どの", "화자와 듣는 사람 모두에게서 멀리 있는 건물을 가리키므로 あの가 알맞습니다."),
            question(1788408430328L, "빈칸에 가장 알맞은 표현을 고르세요.", "私はコーヒーを＿＿＿飲みません。", "あまり", "とても", "いつも", "もう", "부정형과 함께 '그다지'를 나타내므로 あまり가 알맞습니다."),
            question(1788408430344L, "빈칸에 가장 알맞은 표현을 고르세요.", "明日雨が降る＿＿＿、天気予報を見ます。", "かどうか", "から", "だけ", "ながら", "비가 올지 아닐지라는 선택 여부를 나타내므로 かどうか가 알맞습니다."),
            question(1788408430346L, "빈칸에 가장 알맞은 표현을 고르세요.", "今日は日曜日です。＿＿＿、学校は休みです。", "だから", "だけ", "ながら", "しか", "앞 절이 이유이고 뒤 절이 결과이므로 だから가 알맞습니다."),
            question(1788408430364L, "빈칸에 가장 알맞은 표현을 고르세요.", "私は日本の音楽＿＿＿好きです。", "が", "を", "に", "で", "好き의 대상에는 기본적으로 が를 사용합니다."),
            question(1788408430376L, "빈칸에 가장 알맞은 표현을 고르세요.", "私は少し日本語を話す＿＿＿。", "ことができます", "たいです", "てください", "ましょう", "능력을 나타내는 문맥이므로 ことができます가 알맞습니다."),
            question(1788408430380L, "빈칸에 가장 알맞은 표현을 고르세요.", "冷蔵庫には牛乳＿＿＿ありません。", "しか", "だけ", "まで", "ごろ", "しか는 뒤의 부정형과 결합해 '우유밖에 없다'를 나타냅니다."),
            question(1788408430385L, "빈칸에 가장 알맞은 표현을 고르세요.", "宿題をし＿＿＿、テレビを見ます。", "たあとで", "ながら", "ないで", "たいです", "숙제를 끝낸 뒤 텔레비전을 보는 순서이므로 たあとで가 알맞습니다."),
            question(1788408430387L, "빈칸에 가장 알맞은 표현을 고르세요.", "来年、日本へ行き＿＿＿。", "たいです", "ことができます", "てはいけません", "ながら", "화자의 희망을 말하므로 たいです가 알맞습니다."),
            question(1788408430395L, "빈칸에 가장 알맞은 표현을 고르세요.", "今日は水＿＿＿飲みます。", "だけ", "しか", "から", "でも", "긍정문에서 단순히 범위를 제한하므로 だけ가 알맞습니다."),
            question(1788408430400L, "빈칸에 가장 알맞은 표현을 고르세요.", "週末は部屋を掃除する＿＿＿です。", "つもり", "たい", "しか", "ながら", "이미 세운 의도·계획을 말하므로 つもり입니다."),
            question(1788408430406L, "빈칸에 가장 알맞은 표현을 고르세요.", "今、妹は本を読ん＿＿＿。", "でいます", "でから", "でください", "でもいいですか", "지금 진행 중인 행동을 나타내므로 でいます가 알맞습니다."),
            question(1788408430410L, "빈칸에 가장 알맞은 표현을 고르세요.", "朝ごはんを食べ＿＿＿、会社へ行きます。", "てから", "ながら", "ないで", "ても", "먼저 먹고 그다음 회사에 가는 순서이므로 てから가 알맞습니다."),
            question(1788408430412L, "빈칸에 가장 알맞은 표현을 고르세요.", "ここに名前を書い＿＿＿。", "てください", "てもいいですか", "てはいけません", "ながら", "상대에게 행동을 요청하므로 てください가 알맞습니다."),
            question(1788408430421L, "빈칸에 가장 알맞은 표현을 고르세요.", "この部屋で写真を撮っ＿＿＿。", "てはいけません", "てもいいですか", "てください", "ています", "금지 안내이므로 てはいけません이 알맞습니다."),
            question(1788408430425L, "빈칸에 가장 알맞은 표현을 고르세요.", "雨が降っ＿＿＿、行きます。", "ても", "てから", "てください", "ています", "비가 와도 간다는 양보의 뜻이므로 ても가 알맞습니다."),
            question(1788408430427L, "빈칸에 가장 알맞은 표현을 고르세요.", "ここに座っ＿＿＿。", "てもいいですか", "てはいけません", "てください", "ています", "상대에게 허가를 묻는 상황이므로 てもいいですか가 알맞습니다."),
            question(1788408430441L, "빈칸에 가장 알맞은 표현을 고르세요.", "空が暗いです。もうすぐ雨が降る＿＿＿。", "でしょう", "だけ", "ながら", "しか", "눈앞의 단서로 하는 부드러운 추측이므로 でしょう가 알맞습니다."),
            question(1788408430453L, "빈칸에 가장 알맞은 표현을 고르세요.", "朝ごはんを食べ＿＿＿、学校へ行きました。", "ないで", "なくて", "ながら", "たあとで", "먹지 않은 채 다음 행동을 했으므로 ないで가 알맞습니다."),
            question(1788408430455L, "빈칸에 가장 알맞은 표현을 고르세요.", "ここで大きい声で話さ＿＿＿。", "ないでください", "てください", "てもいいですか", "ています", "상대에게 하지 말아 달라고 요청하므로 ないでください가 알맞습니다."),
            question(1788408430457L, "빈칸에 가장 알맞은 표현을 고르세요.", "音楽を聞き＿＿＿、勉強します。", "ながら", "てから", "ないで", "ても", "한 사람이 두 행동을 동시에 하므로 ながら가 알맞습니다."),
            question(1788408430459L, "빈칸에 가장 알맞은 표현을 고르세요.", "時間が＿＿＿、映画を見ませんでした。", "なくて", "ないで", "たあとで", "ながら", "시간이 없는 것이 이유이므로 なくて가 알맞습니다."),
            question(1788408430471L, "빈칸에 가장 알맞은 표현을 고르세요.", "友だちと映画を見＿＿＿行きます。", "に", "で", "を", "が", "이동의 목적을 나타내므로 に가 알맞습니다."),
            question(1788408430475L, "빈칸에 가장 알맞은 표현을 고르세요.", "私は青いかばん＿＿＿します。", "に", "で", "を", "が", "화자가 선택하는 상황이므로 にします의 に가 알맞습니다."),
            question(1788408430478L, "빈칸에 가장 알맞은 표현을 고르세요.", "来月から春＿＿＿なります。", "に", "で", "を", "が", "계절이 변화하는 결과를 말하므로 になります의 に가 알맞습니다."),
            question(1788408430485L, "빈칸에 가장 알맞은 표현을 고르세요.", "友だちが私の家へ遊び＿＿＿来ます。", "に", "で", "を", "が", "무엇을 하러 오는 목적을 나타내므로 に가 알맞습니다."),
            question(1788408430487L, "빈칸에 가장 알맞은 표현을 고르세요.", "日曜日に買い物＿＿＿行きます。", "に", "で", "を", "が", "무엇을 하러 가는 목적을 나타내므로 に가 알맞습니다."),
            question(1788408430505L, "빈칸에 가장 알맞은 표현을 고르세요.", "電車よりバス＿＿＿安いです。", "のほうが", "しか", "ながら", "かどうか", "둘을 비교해 버스가 더 싸다고 하므로 のほうが가 알맞습니다."),
            question(1788408430507L, "빈칸에 가장 알맞은 표현을 고르세요.", "寝る＿＿＿、歯を磨きます。", "前に", "たあとで", "ながら", "ても", "잠들기 이전의 행동을 말하므로 前に가 알맞습니다."),
            question(1788408430511L, "빈칸에 가장 알맞은 표현을 고르세요.", "いっしょに昼ごはんを食べ＿＿＿。", "ましょう", "てください", "てはいけません", "ないでください", "화자와 상대가 함께 하자는 제안이므로 ましょう가 알맞습니다.")
    );
}
