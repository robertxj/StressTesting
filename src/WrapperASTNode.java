package testpopupmenu.handlers;
import org.eclipse.jdt.core.dom.ASTNode;

public class WrapperASTNode{
	private ASTNode node;
	private int level;
	public WrapperASTNode(ASTNode node, int level){
		this.node = node;
		this.level = level;
	}
	public ASTNode getNode() {
		return node;
	}
	public void setNode(ASTNode node) {
		this.node = node;
	}
	public int getLevel() {
		return level;
	}
	public void setLevel(int level) {
		this.level = level;
	}
	public String toString(){
		return "level " + level + " ASTNode: " + node;
	}
	
}